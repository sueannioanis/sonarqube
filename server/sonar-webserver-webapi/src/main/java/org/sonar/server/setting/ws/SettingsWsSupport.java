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
package org.sonar.server.setting.ws;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.server.ServerSide;
import org.sonar.db.entity.EntityDto;
import org.sonar.db.permission.GlobalPermission;
import org.sonar.db.permission.ProjectPermission;
import org.sonar.process.ProcessProperties;
import org.sonar.server.user.UserSession;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.sonar.db.permission.ProjectPermission.ADMIN;

@ServerSide
public class SettingsWsSupport {
  public static final String DOT_SECURED = ".secured";
  @VisibleForTesting
  static final Set<String> ADMIN_ONLY_SETTINGS = Set.of("sonar.auth.bitbucket.workspaces", "sonar.auth.github.organizations");

  private final UserSession userSession;

  public SettingsWsSupport(UserSession userSession) {
    this.userSession = userSession;
  }

  static void validateKey(String key) {
    stream(ProcessProperties.Property.values())
      .filter(property -> property.getKey().equalsIgnoreCase(key))
      .findFirst()
      .ifPresent(property -> {
        throw new IllegalArgumentException(format("Setting '%s' can only be used in sonar.properties", key));
      });
  }

  boolean isVisible(String key, Optional<EntityDto> component) {
    if (isAdmin(component)) {
      return true;
    }
    return hasPermission(GlobalPermission.SCAN, ProjectPermission.SCAN, component) || !isProtected(key);
  }

  private boolean isAdmin(Optional<EntityDto> component) {
    return userSession.isSystemAdministrator() || hasPermission(GlobalPermission.ADMINISTER, ADMIN, component);
  }

  private static boolean isProtected(String key) {
    return isSecured(key) || isAdminOnly(key);
  }

  static boolean isSecured(String key) {
    return key.endsWith(DOT_SECURED);
  }

  private static boolean isAdminOnly(String key) {
    return ADMIN_ONLY_SETTINGS.contains(key);
  }

  private boolean hasPermission(GlobalPermission orgPermission, ProjectPermission projectPermission, Optional<EntityDto> component) {
    if (userSession.hasPermission(orgPermission)) {
      return true;
    }
    return component
      .map(c -> userSession.hasEntityPermission(projectPermission, c))
      .orElse(false);
  }

}
