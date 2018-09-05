/*
 * Copyright 2010-2018 Institut Pasteur.
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
package algorithms.danyfel80.io.sequence.tileprovider;

import java.awt.Point;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import algorithms.danyfel80.io.sequence.cursor.IcyBufferedImageCursor;
import icy.image.IcyBufferedImage;
import icy.type.DataType;

/**
 * @author Daniel Felipe Gonzalez Obando
 *
 */
public class LargeSequenceThresholdedTileProvider extends LargeSequenceTileProvider {

	private List<Double> thresholdValues;
	private boolean invertingClasses;
	private IcyBufferedImage currentTileImage;
	private IcyBufferedImageCursor cursorCurrentTileImage;
	private int currentX, currentY;
	private double currentAverageValue;

	public void setThresholdValues(double[] thresholdValues) {
		this.thresholdValues = Arrays.stream(thresholdValues).sorted().boxed().collect(Collectors.toList());
	}

	public void setInvertingClasses(boolean invertingClasses) {
		this.invertingClasses = invertingClasses;
	}

	@Override
	public IcyBufferedImage getTile(Point tileIndex) throws IOException {
		currentTileImage = super.getTile(tileIndex);
		cursorCurrentTileImage = new IcyBufferedImageCursor(currentTileImage);

		IcyBufferedImage thresholdedTileImage = new IcyBufferedImage(currentTileImage.getSizeX(),
				currentTileImage.getSizeY(), 1, DataType.UBYTE);
		IcyBufferedImageCursor cursorThresholdedTileImage = new IcyBufferedImageCursor(thresholdedTileImage);

		for (currentY = 0; currentY < currentTileImage.getSizeY(); currentY++) {
			for (currentX = 0; currentX < currentTileImage.getSizeX(); currentX++) {
				computeCurrentAverageValue();
				int bin = getBinValue();
				cursorThresholdedTileImage.setSafe(currentX, currentY, 0, bin);
			}
		}
		cursorThresholdedTileImage.commitChanges();
		return thresholdedTileImage;
	}

	private void computeCurrentAverageValue() {
		currentAverageValue = 0d;
		int count = 0;
		for (int c = 0; c < currentTileImage.getSizeC(); c++) {
			count += 1;
			currentAverageValue += (cursorCurrentTileImage.get(currentX, currentY, c) - currentAverageValue) / count;
		}
	}

	private int getBinValue() {
		int index = Collections.binarySearch(thresholdValues, currentAverageValue);
		if (index < 0)
			index = Math.abs(index) - 1;

		if (invertingClasses)
			index = thresholdValues.size() - index;

		return index;
	}
}
