/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.caliper.runner.instrument;

import static com.google.caliper.runner.instrument.CommonInstrumentOptions.GC_BEFORE_EACH_OPTION;

import com.google.caliper.bridge.AbstractLogMessageVisitor;
import com.google.caliper.bridge.StopMeasurementLogMessage;
import com.google.caliper.core.BenchmarkClassModel.MethodModel;
import com.google.caliper.core.InvalidBenchmarkException;
import com.google.caliper.model.ArbitraryMeasurement;
import com.google.caliper.model.InstrumentType;
import com.google.caliper.model.Measurement;
import com.google.caliper.runner.config.SupportsVmType;
import com.google.caliper.runner.config.VmType;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.lang.reflect.Modifier;

/**
 * Instrument for taking an arbitrary measurement. When using this instrument, the benchmark code
 * itself returns the value. See {@link ArbitraryMeasurement}.
 */
@SupportsVmType(VmType.JVM)
public final class ArbitraryMeasurementInstrument extends Instrument {

  @Override
  public boolean isBenchmarkMethod(MethodModel method) {
    return method.isAnnotationPresent(ArbitraryMeasurement.class);
  }

  @Override
  public InstrumentedMethod createInstrumentedMethod(MethodModel benchmarkMethod)
      throws InvalidBenchmarkException {
    if (!benchmarkMethod.parameterTypes().isEmpty()) {
      throw new InvalidBenchmarkException(
          "Arbitrary measurement methods should take no parameters: " + benchmarkMethod.name());
    }

    if (!benchmarkMethod.returnType().isPresent()
        || !benchmarkMethod.returnType().get().equals("double")) {
      throw new InvalidBenchmarkException(
          "Arbitrary measurement methods must have a return type of double: "
              + benchmarkMethod.name());
    }

    // Static technically doesn't hurt anything, but it's just the completely wrong idea
    if (Modifier.isStatic(benchmarkMethod.modifiers())) {
      throw new InvalidBenchmarkException(
          "Arbitrary measurement methods must not be static: " + benchmarkMethod.name());
    }

    if (!Modifier.isPublic(benchmarkMethod.modifiers())) {
      throw new InvalidBenchmarkException(
          "Arbitrary measurement methods must be public: " + benchmarkMethod.name());
    }

    return new ArbitraryMeasurementInstrumentedMethod(benchmarkMethod);
  }

  @Override
  public boolean parallelizable() {
    // We could allow it here but in general it would depend on the particular measurement so it
    // should probably be configured by the user.  For now we just disable it.
    return false;
  }

  private final class ArbitraryMeasurementInstrumentedMethod extends InstrumentedMethod {
    protected ArbitraryMeasurementInstrumentedMethod(MethodModel benchmarkMethod) {
      super(benchmarkMethod);
    }

    @Override
    public InstrumentType type() {
      return InstrumentType.ARBITRARY_MEASUREMENT;
    }

    @Override
    public ImmutableMap<String, String> workerOptions() {
      return ImmutableMap.of(GC_BEFORE_EACH_OPTION, options.get(GC_BEFORE_EACH_OPTION));
    }

    @Override
    public MeasurementCollectingVisitor getMeasurementCollectingVisitor() {
      return new SingleMeasurementCollectingVisitor();
    }
  }

  @Override
  public ImmutableSet<String> instrumentOptions() {
    return ImmutableSet.of(GC_BEFORE_EACH_OPTION);
  }

  private static final class SingleMeasurementCollectingVisitor extends AbstractLogMessageVisitor
      implements MeasurementCollectingVisitor {
    Optional<Measurement> measurement = Optional.absent();

    @Override
    public boolean isDoneCollecting() {
      return measurement.isPresent();
    }

    @Override
    public boolean isWarmupComplete() {
      return true;
    }

    @Override
    public ImmutableList<Measurement> getMeasurements() {
      return ImmutableList.copyOf(measurement.asSet());
    }

    @Override
    public void visit(StopMeasurementLogMessage logMessage) {
      this.measurement = Optional.of(Iterables.getOnlyElement(logMessage.measurements()));
    }

    @Override
    public ImmutableList<String> getMessages() {
      return ImmutableList.of();
    }
  }
}
