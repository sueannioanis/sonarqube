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
package org.sonar.server.loginmessage;

import org.sonar.api.SonarRuntime;
import org.sonar.api.server.ServerSide;
import org.sonar.server.feature.SonarQubeFeature;

import static org.sonar.api.SonarEdition.DATACENTER;
import static org.sonar.api.SonarEdition.ENTERPRISE;

@ServerSide
public class LoginMessageFeature implements SonarQubeFeature {

  private final SonarRuntime sonarRuntime;

  public LoginMessageFeature(SonarRuntime sonarRuntime) {
    this.sonarRuntime = sonarRuntime;
  }

  @Override
  public String getName() {
    return "login-message";
  }

  @Override
  public boolean isAvailable() {
    return sonarRuntime.getEdition() == ENTERPRISE || sonarRuntime.getEdition() == DATACENTER;
  }

}
