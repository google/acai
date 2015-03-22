/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.acai;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Internal helper methods for managing {@code TestingService} dependencies
 * which have been declared using the {@code DependsOn} annotation.
 */
class Dependencies {

  /**
   * Returns a valid execution order for {@code testingServices} based on
   * any {@link DependsOn} annotations present.
   *
   * @throws IllegalArgumentException if the dependency graph contains a cycle
   */
  static ImmutableList<TestingService> inOrder(Set<TestingService> testingServices) {
    return topologicalSorting(buildDependencyGraph(testingServices));
  }

  /**
   * Returns a topological sorting.
   *
   * <p>Algorithm due to Kahn, Arthur B. (1962), Topological sorting of large networks,
   * Communications of the ACM 5 (11): 558–562,
   * <a href="http://dl.acm.org/citation.cfm?doid=368996.369025">doi:10.1145/368996.369025</a>.
   */
  private static ImmutableList<TestingService> topologicalSorting(
      DirectedGraph<TestingService> dependencyGraph) {
    Queue<TestingService> rootVertices = new ArrayDeque<>(dependencyGraph.getRootVertices());
    ImmutableList.Builder<TestingService> ordered = ImmutableList.builder();
    while (!rootVertices.isEmpty()) {
      TestingService vertex = rootVertices.remove();
      ordered.add(vertex);
      for (TestingService successor : dependencyGraph.getSuccessors(vertex)) {
        dependencyGraph.removeEdge(vertex, successor);
        if (dependencyGraph.isRootVertex(successor)) {
          rootVertices.add(successor);
        }
      }
    }
    if (dependencyGraph.hasEdges()) {
      throw new IllegalArgumentException("Cycle exists in @DependsOn dependencies.");
    }
    return ordered.build();
  }

  /**
   * Returns a directed graph representing the dependencies of {@code testingServices}.
   */
  private static DirectedGraph<TestingService> buildDependencyGraph(
      Set<TestingService> testingServices) {
    DirectedGraph<TestingService> dependencyGraph = new DirectedGraph<>(testingServices);
    Multimap<Class<? extends TestingService>, TestingService> servicesByClass =
        HashMultimap.create();
    for (TestingService testingService : testingServices) {
      servicesByClass.put(testingService.getClass(), testingService);
    }
    for (TestingService testingService : testingServices) {
      for (TestingService dependency : getDependencies(testingService, servicesByClass)) {
        dependencyGraph.addEdge(dependency, testingService);
      }
    }
    return dependencyGraph;
  }

  /**
   * Returns the set of services which {@code testingService} depends upon.
   */
  private static ImmutableSet<TestingService> getDependencies(
      TestingService testingService,
      Multimap<Class<? extends TestingService>, TestingService> servicesByClass) {
    if (!testingService.getClass().isAnnotationPresent(DependsOn.class)) {
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<TestingService> dependencies = ImmutableSet.builder();
    DependsOn dependsOn = testingService.getClass().getAnnotation(DependsOn.class);
    for (Class<? extends TestingService> serviceClass : dependsOn.value()) {
      dependencies.addAll(servicesByClass.get(serviceClass));
    }
    return dependencies.build();
  }

  /**
   * Simple representation of a directed graph.
   *
   * <p>The set of vertices in the graph is immutable but edges may be
   * added and removed from an instance.
   */
  private static class DirectedGraph<T> {
    private final Multimap<T, T> successors = HashMultimap.create();
    private final Multimap<T, T> predecessors = HashMultimap.create();
    private final ImmutableSet<T> vertices;

    /**
     * Initializes a graph containing {@code vertices}.
     */
    DirectedGraph(Set<T> vertices) {
      this.vertices = ImmutableSet.copyOf(vertices);
    }

    /**
     * Adds a directed edge from {@code tail} to {@code head}.
     */
    void addEdge(T tail, T head) {
      successors.put(tail, head);
      predecessors.put(head, tail);
    }

    /**
     * Removes the directed edge from {@code tail} to {@code head}.
     */
    void removeEdge(T tail, T head) {
      checkArgument(successors.remove(tail, head), "Attempt to remove non-existent edge");
      checkState(predecessors.remove(head, tail), "Graph state was invalid.");
    }

    /**
     * Returns the set of vertices which are not the tail of any directed edge.
     */
    Set<T> getRootVertices() {
      return Sets.difference(vertices, predecessors.keySet());
    }

    /**
     * Returns true if there are no directed edges
     */
    boolean isRootVertex(T vertex) {
      checkArgument(vertices.contains(vertex));
      return !predecessors.containsKey(vertex);
    }

    /**
     * Returns true if the graph has any edges.
     */
    boolean hasEdges() {
      return !successors.isEmpty();
    }

    /**
     * Returns the set of vertices who are at the head of an edge whose tail is {@code vertex}.
     */
    ImmutableSet<T> getSuccessors(T vertex) {
      return ImmutableSet.copyOf(successors.get(vertex));
    }
  }
}
