/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.deprecated.decorator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.batch.DefaultFormulaContext;
import org.sonar.api.batch.DefaultFormulaData;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.measures.FormulaData;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;

/**
 * A pre-implementation of a decorator using a simple calculation formula
 * @since 1.11
 */
public final class FormulaDecorator implements Decorator {

  private Metric metric;
  private DefaultFormulaContext formulaContext;
  private Set<Decorator> executeAfterDecorators;

  /**
   * Creates a FormulaDecorator
   *
   * @param metric the metric should have an associated formula
   * 
   * @throws IllegalArgumentException if no formula is associated to the metric
   */
  public FormulaDecorator(Metric metric, Set<Decorator> executeAfterDecorators) {
    if (metric.getFormula() == null) {
      throw new IllegalArgumentException("No formula defined on metric");
    }
    this.metric = metric;
    this.formulaContext = new DefaultFormulaContext(metric);
    this.executeAfterDecorators = executeAfterDecorators;
  }

  public FormulaDecorator(Metric metric) {
    this(metric, Collections.<Decorator>emptySet());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return true;
  }

  /**
   * @return metric generated by the decorator
   */
  @DependedUpon
  public Metric generatesMetric() {
    return metric;
  }

  /**
   * @return metric the decorator depends upon
   */
  @DependsUpon
  public List<Metric> dependsUponMetrics() {
    return metric.getFormula().dependsUponMetrics();
  }

  @DependsUpon
  public Collection<Decorator> dependsUponDecorators() {
    return executeAfterDecorators;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void decorate(Resource resource, DecoratorContext context) {
    if (context.getMeasure(metric) != null) {
      return;
    }

    formulaContext.setDecoratorContext(context);
    FormulaData data = new DefaultFormulaData(context);
    Measure measure = metric.getFormula().calculate(data, formulaContext);
    if (measure != null) {
      context.saveMeasure(measure);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FormulaDecorator that = (FormulaDecorator) o;
    return !(metric != null ? !metric.equals(that.metric) : that.metric != null);
  }

  @Override
  public int hashCode() {
    return metric != null ? metric.hashCode() : 0;
  }

  @Override
  public String toString() {
    return new StringBuilder().append("f(").append(metric.getKey()).append(")").toString();
  }
}
