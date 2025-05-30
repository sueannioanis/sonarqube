/*
 * SonarQube
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.permission.ws;

import org.junit.Before;
import org.junit.Test;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentQualifiers;
import org.sonar.db.permission.GlobalPermission;
import org.sonar.db.permission.ProjectPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.common.management.ManagedInstanceChecker;
import org.sonar.server.component.ComponentTypes;
import org.sonar.server.component.ComponentTypesRule;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.permission.PermissionService;
import org.sonar.server.permission.PermissionServiceImpl;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.sonar.db.component.ComponentTesting.newDirectory;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newSubPortfolio;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PERMISSION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PROJECT_ID;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PROJECT_KEY;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_USER_LOGIN;

public class RemoveUserActionIT extends BasePermissionWsIT<RemoveUserAction> {

  private static final String A_LOGIN = "ray.bradbury";

  private UserDto user;
  private final ComponentTypes componentTypes = new ComponentTypesRule().setRootQualifiers(ComponentQualifiers.PROJECT);
  private final PermissionService permissionService = new PermissionServiceImpl(componentTypes);
  private final WsParameters wsParameters = new WsParameters(permissionService);
  private final ManagedInstanceChecker managedInstanceChecker = mock(ManagedInstanceChecker.class);

  @Before
  public void setUp() {
    user = db.users().insertUser(A_LOGIN);
  }

  @Override
  protected RemoveUserAction buildWsAction() {
    return new RemoveUserAction(db.getDbClient(), userSession, newPermissionUpdater(), newPermissionWsSupport(), wsParameters, permissionService, managedInstanceChecker);
  }

  @Test
  public void wsAction_shouldRemovePermissionFromUser() {
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.PROVISION_PROJECTS);
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.ADMINISTER_QUALITY_GATES);
    loginAsAdmin();

    newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER_QUALITY_GATES.getKey())
      .execute();

    assertThat(db.users().selectPermissionsOfUser(user)).containsOnly(GlobalPermission.PROVISION_PROJECTS);
  }

  @Test
  public void wsAction_whenAdminRemoveOwnGlobalAdminRight_shouldFail() {
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.ADMINISTER);
    loginAsAdmin();
    UserDto admin = db.users().insertUser(userSession.getLogin());
    db.users().insertGlobalPermissionOnUser(admin, GlobalPermission.ADMINISTER);

    TestRequest request = newRequest()
      .setParam(PARAM_USER_LOGIN, userSession.getLogin())
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(request::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("As an admin, you can't remove your own admin right");
  }

  @Test
  public void wsAction_whenProjectAdminRemoveOwnProjectAdminRight_shouldFail() {
    loginAsAdmin();
    UserDto admin = db.users().insertUser(userSession.getLogin());
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    db.users().insertProjectPermissionOnUser(admin, GlobalPermission.ADMINISTER.getKey(), project);

    TestRequest request = newRequest()
      .setParam(PARAM_USER_LOGIN, userSession.getLogin())
      .setParam(PARAM_PROJECT_ID, project.uuid())
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(request::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("As an admin, you can't remove your own admin right");
  }

  @Test
  public void wsAction_whenSystemAdminRemovesOwnBrowsePermission_shouldSucceed() {
    loginAsAdmin();
    UserDto admin = db.users().insertUser(requireNonNull(userSession.getLogin()));
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(admin, GlobalPermission.ADMINISTER.getKey(), project);

    TestRequest request = removeBrowseRight(project);

    request.execute();
    assertThat(db.users().selectPermissionsOfUser(user)).isEmpty();
  }

  @Test
  public void wsAction_whenPrivateProjectAdminRemovesOwnBrowsePermissionButHasPermissionViaGroup_shouldSucceed() {
    UserSessionRule userSessionRule = userSession.logIn();

    UserDto admin = db.users().insertUser(requireNonNull(userSession.getLogin()));
    GroupDto projectAdmins = createGroupAndAddUser(admin, userSessionRule);

    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(admin, ProjectPermission.USER, project);
    db.users().insertEntityPermissionOnGroup(projectAdmins, ProjectPermission.USER, project);
    userSessionRule.addProjectPermission(ProjectPermission.ADMIN, project);

    TestRequest request = removeBrowseRight(project);

    request.execute();
    assertThat(db.users().selectEntityPermissionOfUser(user, project.getUuid())).isEmpty();
  }

  private GroupDto createGroupAndAddUser(UserDto admin, UserSessionRule userSessionRule) {
    GroupDto projectAdmins = db.users().insertGroup("project admins");
    db.users().insertMember(projectAdmins, admin);
    userSessionRule.setGroups(projectAdmins);
    return projectAdmins;
  }

  @Test
  public void wsAction_whenPrivateProjectAdminRemovesOwnBrowsePermission_shouldFail() {
    userSession.logIn();
    UserDto admin = db.users().insertUser(requireNonNull(userSession.getLogin()));
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(admin, GlobalPermission.ADMINISTER.getKey(), project);

    TestRequest request = removeBrowseRight(project);

    assertThatThrownBy(request::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Permission 'Browse' cannot be removed from a private project for a project administrator.");
  }

  private TestRequest removeBrowseRight(ProjectDto project) {
    return newRequest()
      .setParam(PARAM_USER_LOGIN, userSession.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.USER.getKey());
  }

  @Test
  public void wsAction_whenRemoveAdminPermissionAndLastAdmin_shouldFail() {
    db.users().insertGlobalPermissionOnUser(user, GlobalPermission.ADMINISTER);
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, ProjectPermission.ADMIN.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Last user with permission 'admin'. Permission cannot be removed.");
  }

  @Test
  public void wsAction_whenProject_shouldRemovePermission() {
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.CODEVIEWER, project);
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.ISSUE_ADMIN, project);
    loginAsAdmin();

    newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.CODEVIEWER.getKey())
      .execute();

    assertThat(db.users().selectEntityPermissionOfUser(user, project.getUuid())).containsOnly(ProjectPermission.ISSUE_ADMIN.getKey());
  }

  @Test
  public void wsAction_whenUsingProjectKey_shouldRemovePermission() {
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.ISSUE_ADMIN, project);
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.CODEVIEWER, project);
    loginAsAdmin();

    newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_KEY, project.getKey())
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey())
      .execute();

    assertThat(db.users().selectEntityPermissionOfUser(user, project.getUuid())).containsOnly(ProjectPermission.CODEVIEWER.getKey());
  }

  @Test
  public void wsAction_whenUsingViewUuid_shouldRemovePermission() {
    ComponentDto view = db.components().insertPrivatePortfolio();
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.ISSUE_ADMIN, view);
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.ADMIN, view);
    loginAsAdmin();

    newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_KEY, view.getKey())
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey())
      .execute();

    assertThat(db.users().selectEntityPermissionOfUser(user, view.uuid())).containsOnly(ProjectPermission.ADMIN.getKey());
  }

  @Test
  public void wsAction_whenProjectNotFound_shouldFail() {
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, "unknown-project-uuid")
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(NotFoundException.class);
  }

  @Test
  public void wsAction_whenRemovingProjectPermissionWithoutProject_shouldFail() {
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(BadRequestException.class);
  }

  @Test
  public void wsAction_whenComponentIsDirectory_shouldFail() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newDirectory(project, "A/B"));

    failIfComponentIsNotAProjectOrView(file);
  }

  @Test
  public void wsAction_whenComponentIsFile_shouldFail() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project, null, "file-uuid"));

    failIfComponentIsNotAProjectOrView(file);
  }

  @Test
  public void wsAction_whenComponentIsSubview_shouldFail() {
    ComponentDto portfolio = db.components().insertPrivatePortfolio();
    ComponentDto file = db.components().insertComponent(newSubPortfolio(portfolio));

    failIfComponentIsNotAProjectOrView(file);
  }

  private void failIfComponentIsNotAProjectOrView(ComponentDto file) {
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, file.uuid())
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Entity not found");
  }

  @Test
  public void wsAction_whenProjectAndUserAreManaged_shouldThrowAndNotRemovePermissions() {
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.CODEVIEWER, project);

    doThrow(new IllegalStateException("Managed project")).when(managedInstanceChecker).throwIfUserAndProjectAreManaged(any(), eq(user.getUuid()), eq(project.getUuid()));

    loginAsAdmin();
    TestRequest request = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.CODEVIEWER.getKey());

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Managed project");

    assertThat(db.users().selectEntityPermissionOfUser(user, project.getUuid())).containsOnly(ProjectPermission.CODEVIEWER.getKey());
  }

  @Test
  public void wsAction_whenGetRequest_shouldFail() {
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setMethod("GET")
      .setParam(PARAM_USER_LOGIN, "george.orwell")
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(ServerException.class);
  }

  @Test
  public void wsAction_whenUserLoginIsMissing_shouldFail() {
    loginAsAdmin();

    TestRequest testRequest = newRequest().setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void wsAction_whenPermissionIsMissing_shouldFail() {
    loginAsAdmin();

    TestRequest testRequest = newRequest().setParam(PARAM_USER_LOGIN, user.getLogin());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void wsAction_whenProjectUuidAndProjectKeyProvided_shouldFail() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    loginAsAdmin();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey())
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.uuid())
      .setParam(PARAM_PROJECT_KEY, project.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Project id or project key can be provided, not both.");
  }

  @Test
  public void wsAction_whenGlobalPermissionAndNotSystemAdmin_shouldFail() {
    userSession.logIn();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, GlobalPermission.PROVISION_PROJECTS.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void wsAction_whenProjectPermissionAndNotProjectAdmin_shouldFail() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    userSession.logIn();

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey())
      .setParam(PARAM_PROJECT_KEY, project.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(ForbiddenException.class);
  }

  /**
   * User is project administrator but not system administrator
   */
  @Test
  public void wsAction_whenProjectPermissionAndProjectAdmin_shouldRemovePermission() {
    ProjectDto project = db.components().insertPrivateProject().getProjectDto();
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.CODEVIEWER, project);
    db.users().insertProjectPermissionOnUser(user, ProjectPermission.ISSUE_ADMIN, project);
    userSession.logIn().addProjectPermission(ProjectPermission.ADMIN, project);

    newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.ISSUE_ADMIN.getKey())
      .execute();

    assertThat(db.users().selectEntityPermissionOfUser(user, project.getUuid())).containsOnly(ProjectPermission.CODEVIEWER.getKey());
  }

  @Test
  public void wsAction_whenBrowsePermissionAndPublicProject_shouldFail() {
    ProjectDto project = db.components().insertPublicProject().getProjectDto();
    userSession.logIn().addProjectPermission(ProjectPermission.ADMIN, project);

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.USER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Permission user can't be removed from a public component");

  }

  @Test
  public void wsAction_whenCodeviewerPermissionAndPublicProject_shouldFail() {
    ProjectDto project = db.components().insertPublicProject().getProjectDto();
    userSession.logIn().addProjectPermission(ProjectPermission.ADMIN, project);

    TestRequest testRequest = newRequest()
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.getUuid())
      .setParam(PARAM_PERMISSION, ProjectPermission.CODEVIEWER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Permission codeviewer can't be removed from a public component");
  }

  @Test
  public void wsAction_whenUsingBranchUuid_shouldFail() {
    ComponentDto project = db.components().insertPublicProject().getMainBranchComponent();
    userSession.logIn().addProjectPermission(ProjectPermission.ADMIN, project);
    ComponentDto branch = db.components().insertProjectBranch(project);

    TestRequest testRequest = newRequest()
      .setParam(PARAM_PROJECT_ID, branch.uuid())
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, GlobalPermission.ADMINISTER.getKey());

    assertThatThrownBy(testRequest::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("Entity not found");
  }

}
