/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.mzmine.modules.visualization.massvoltammogram;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_ecmscalcpotential.EcmsUtils;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class MassvoltammogramTask extends AbstractTask {
  private double potentialRampSpeed;
  /**
   * step size between drawn spectra in mV
   */
  private double stepSize;
  /**
   * potential range of mass voltammogram in mV
   */
  private final Range<Double> potentialRange;
  /**
   * m/z range of drawn spectra
   */
  private final Range<Double> mzRange;
  /**
   * Mode of the EC/MS experiment.
   */
  private final ReactionMode reactionMode;
  /**
   * The beginning of the potential ramp.
   */
  private double startPotential;
  /**
   * The end of the potential ramp.
   */
  private double endPotential;
  /**
   * The scan selection filter.
   */
  private final ScanSelection scanSelection;
  /**
   * Delay time between the EC cell and the MS in s.
   */
  private final double delayTime;

  public MassvoltammogramTask(@NotNull ParameterSet parameters, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate);
    potentialRampSpeed = parameters.getValue(MassvoltammogramParameters.potentialRampSpeed);
    stepSize = parameters.getValue(MassvoltammogramParameters.stepSize);
    potentialRange = parameters.getValue(MassvoltammogramParameters.potentialRange);
    mzRange = parameters.getValue(MassvoltammogramParameters.mzRange);
    reactionMode = parameters.getValue(MassvoltammogramParameters.reactionMode);
    delayTime = parameters.getValue(MassvoltammogramParameters.delayTime);
    scanSelection = parameters.getValue(MassvoltammogramParameters.scanSelection);
  }

  @Override
  public String getTaskDescription() {
    return "Creating Massvoltammogram";
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    //Getting raw data file.
    final RawDataFile file = MassvoltammogramParameters.files.getValue()
        .getMatchingRawDataFiles()[0];

    //Setting up the parameters correctly depending on the selected reaction type.
    if (reactionMode.equals(ReactionMode.OXIDATIVE)) {
      startPotential = potentialRange.lowerEndpoint();
      endPotential = potentialRange.upperEndpoint();
      potentialRampSpeed = Math.abs(potentialRampSpeed);
      stepSize = Math.abs(stepSize);
    } else if (reactionMode.equals(ReactionMode.REDUCTIVE)) {
      startPotential = potentialRange.upperEndpoint();
      endPotential = potentialRange.lowerEndpoint();
      potentialRampSpeed = Math.abs(potentialRampSpeed) * -1;
      stepSize = Math.abs(stepSize) * -1;
    }

    //Creating a list with all needed scans.
    final List<double[][]> scans = MassvoltammogramUtils.getScans(file, scanSelection,
        delayTime / 60, startPotential, endPotential, potentialRampSpeed, stepSize);

    //Checking weather the scans were extracted correctly.
    if (scans.size() == 0) {
      setStatus(TaskStatus.ERROR);
      setErrorMessage(
          "The entered parameters do not match the selected data file!\nThe massvolatammogarm cannot be created.\nCheck the entered parameters for plausibility.");
      return;
    }

    //Extracting all spectra within the given m/z-range.
    final List<double[][]> spectra = MassvoltammogramUtils.extractMZRangeFromScan(scans, mzRange);

    //Getting the maximal intensity from all spectra.
    final double maxIntensity = MassvoltammogramUtils.getMaxIntensity(spectra);

    //Removing all datapoints with low intensity values.
    final List<double[][]> spectraWithoutNoise = MassvoltammogramUtils.removeNoise(spectra,
        maxIntensity);

    //Removing excess zeros from the dataset.
    final List<double[][]> spectraWithoutZeros = MassvoltammogramUtils.removeExcessZeros(
        spectraWithoutNoise);

    //Creating new 3D Plot.
    final ExtendedPlot3DPanel plot = new ExtendedPlot3DPanel();

    //Adding the data to the plot for later export.
    plot.addRawScans(scans);
    plot.addRawScansInMzRange(spectra);

    //Calculating the divisor needed to scale the z-axis.
    final double divisor = MassvoltammogramUtils.getDivisor(maxIntensity);

    //Adding all the spectra to the plot.
    MassvoltammogramUtils.addSpectraToPlot(spectraWithoutZeros, divisor, plot);

    //Setting up the plot correctly.
    plot.setAxisLabels("m/z", "Potential / mV",
        "Intensity / 10" + MassvoltammogramUtils.toSupercript((int) Math.log10(divisor)) + " a.u.");
    plot.setFixedBounds(1, potentialRange.lowerEndpoint(), potentialRange.upperEndpoint());
    plot.setFixedBounds(0, mzRange.lowerEndpoint(), mzRange.upperEndpoint());

    //Adding the plot to a new MZmineTab.
    final MassvoltammogramTab mvTab = new MassvoltammogramTab("Massvoltammogram", plot,
        file.getName());
    MZmineCore.getDesktop().addTab(mvTab);

    setStatus(TaskStatus.FINISHED);
  }
}