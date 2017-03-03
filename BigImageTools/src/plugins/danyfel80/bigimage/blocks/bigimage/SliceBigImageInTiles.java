/*
 * Copyright 2010-2016 Institut Pasteur.
 * 
 * This file is part of Icy.
 * 
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <http://www.gnu.org/licenses/>.
 */
package plugins.danyfel80.bigimage.blocks.bigimage;

import java.awt.Dimension;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import algorithms.danyfel80.bigimage.io.BigImageTileSlicer;
import icy.common.listener.RichProgressListener;
import icy.gui.dialog.MessageDialog;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVar;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarDoubleArrayNative;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarFolder;
import plugins.adufour.ezplug.EzVarIntegerArrayNative;
import plugins.adufour.ezplug.EzVarListener;

/**
 * @author Daniel Felipe Gonzalez Obando
 */
public class SliceBigImageInTiles extends EzPlug implements Block, EzStoppable {

	private EzVarFile								varInFile;
	private EzVarFolder							varInOutputFolder;
	private EzVarIntegerArrayNative	varInTileSize;
	private EzVarDoubleArrayNative	varInBackgroundColor;
	private EzVarDouble							varInVarianceThreshold;

	private ExecutorService executor;

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
		initializeVariables();

		varInFile.addVarChangeListener(new EzVarListener<File>() {
			@Override
			public void variableChanged(EzVar<File> source, File newValue) {
				if (newValue != null && varInFile.getValue() != null) {
					varInOutputFolder.setValue(
							newValue.toPath().getParent().resolve(varInFile.getValue().toPath().getFileName() + "_Tiles").toFile());
				}
			}
		});

		addEzComponent(varInFile);
		addEzComponent(varInOutputFolder);
		addEzComponent(varInTileSize);
		addEzComponent(varInBackgroundColor);
		addEzComponent(varInVarianceThreshold);
	}

	/**
	 * Initializes the variables of this plugin.
	 */
	private void initializeVariables() {
		varInFile = new EzVarFile("Input file", null);
		varInOutputFolder = new EzVarFolder("Output folder", null);
		varInTileSize = new EzVarIntegerArrayNative("Tile size", new int[][] { { 2000, 2000 } }, true);
		varInBackgroundColor = new EzVarDoubleArrayNative("Background color", new double[][] { { 255, 255, 255 } }, true);
		varInVarianceThreshold = new EzVarDouble("Variance from background threshold", 60, 0.5, Double.MAX_VALUE, 0.1);
	}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		BigImageTileSlicer tileSlicer = new BigImageTileSlicer(varInFile.getValue().toPath(),
				varInOutputFolder.getValue().toPath(), new Dimension(varInTileSize.getValue()[0], varInTileSize.getValue()[1]),
				varInBackgroundColor.getValue(), varInVarianceThreshold.getValue());

		if (this.getUI() != null) {
			tileSlicer.addProgressListener(new RichProgressListener() {
				@Override
				public boolean notifyProgress(double position, double length, String comment, Object data) {
					getUI().setProgressBarMessage(comment);
					getUI().setProgressBarValue(position / length);
					return true;
				}
			});
		}

		executor = Executors.newSingleThreadExecutor();

		Future<Void> result = executor.submit(tileSlicer);

		executor.shutdown();

		try {
			result.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
			MessageDialog.showDialog("Error", "Process interrupted", MessageDialog.ERROR_MESSAGE);
			return;
		} catch (ExecutionException e) {
			e.printStackTrace();
			MessageDialog.showDialog("Error", e.getMessage(), MessageDialog.ERROR_MESSAGE);
			return;
		}

	}

	/*
	 * (non-Javadoc)
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareInput(plugins.adufour.blocks.util.
	 * VarList)
	 */
	@Override
	public void declareInput(VarList inputMap) {
		initializeVariables();
		inputMap.add(varInFile.name, varInFile.getVariable());
		inputMap.add(varInOutputFolder.name, varInOutputFolder.getVariable());
		inputMap.add(varInTileSize.name, varInTileSize.getVariable());
		inputMap.add(varInBackgroundColor.name, varInBackgroundColor.getVariable());
		inputMap.add(varInVarianceThreshold.name, varInVarianceThreshold.getVariable());
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * plugins.adufour.blocks.lang.Block#declareOutput(plugins.adufour.blocks.util
	 * .VarList)
	 */
	@Override
	public void declareOutput(VarList outputMap) {}

	/*
	 * (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {}

	@Override
	public void stopExecution() {
		executor.shutdownNow();
		super.stopExecution();
	}

}
