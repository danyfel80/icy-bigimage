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
package impl.danyfel80.sequence.util;

import java.util.concurrent.atomic.AtomicBoolean;

import icy.sequence.Sequence;
import icy.type.DataType;
import icy.type.TypeUtil;

/**
 * @author Daniel Felipe Gonzalez Obando
 */
public class SequenceVolumeCursor {

	Sequence	seq;
	int[]			seqSize;
	DataType	seqType;
	int				t;

	AtomicBoolean	tChanged;
	AtomicBoolean	volumeChanged;

	Object volumeData;

	/**
	 * 
	 */
	public SequenceVolumeCursor(Sequence seq) {
		this.seq = seq;
		this.seqSize = new int[] { seq.getSizeX(), seq.getSizeY(), seq.getSizeZ() };
		this.seqType = seq.getDataType_();
		tChanged = new AtomicBoolean(true);
		volumeChanged = new AtomicBoolean(false);
	}

	public SequenceVolumeCursor(Sequence seq, int t) {
		this(seq);
		setT(t);
	}

	public void setT(int t) {
		this.t = t;
		tChanged.set(true);
	}

	public void setVolume(int t) {
		if (volumeChanged.get()) {
			seq.dataChanged();
			volumeChanged.set(false);
		}
		volumeData = seq.getDataXYCZ(t);
		tChanged.set(false);
	}

	public synchronized double get(int x, int y, int z, int c, boolean withOrigin) {
		if (tChanged.get()) {
			setVolume(t);
		}

		if (withOrigin) {
			x += seq.getBounds5D().x;
			y += seq.getBounds5D().y;
			z += seq.getBounds5D().z;
		}
		switch (seqType) {
		case UBYTE:
		case BYTE:
			return TypeUtil.toDouble(((byte[][][]) volumeData)[z][c][x + y * seqSize[0]], seqType.isSigned());
		case USHORT:
		case SHORT:
			return TypeUtil.toDouble(((short[][][]) volumeData)[z][c][x + y * seqSize[0]], seqType.isSigned());
		case UINT:
		case INT:
			return TypeUtil.toDouble(((int[][][]) volumeData)[z][c][x + y * seqSize[0]], seqType.isSigned());
		case FLOAT:
			return ((float[][][]) volumeData)[z][c][x + y * seqSize[0]];
		case DOUBLE:
			return ((double[][][]) volumeData)[z][c][x + y * seqSize[0]];
		default:
			throw new RuntimeException("Unsupported data type: " + seqType);
		}
	}

	public synchronized void set(int x, int y, int z, int c, double val) {
		if (tChanged.get()) {
			setVolume(t);
		}

		switch (seqType) {
		case UBYTE:
		case BYTE:
			((byte[][][]) volumeData)[z][c][x + y * seqSize[0]] = (byte) val;
		break;
		case USHORT:
		case SHORT:
			((short[][][]) volumeData)[z][c][x + y * seqSize[0]] = (short) val;
		break;
		case UINT:
		case INT:
			((int[][][]) volumeData)[z][c][x + y * seqSize[0]] = (int) val;
		break;
		case FLOAT:
			((float[][][]) volumeData)[z][c][x + y * seqSize[0]] = (float) val;
		break;
		case DOUBLE:
			((double[][][]) volumeData)[z][c][x + y * seqSize[0]] = val;
		break;
		default:
			throw new RuntimeException("Unsupported data type");
		}
		volumeChanged.set(true);
	}

	public synchronized void setSafe(int x, int y, int z, int c, double val) {
		if (tChanged.get()) {
			setVolume(t);
		}

		switch (seqType) {
		case UBYTE:
		case BYTE:
			((byte[][][]) volumeData)[z][c][x + y * seqSize[0]] = (byte) getSafeValue(val);
		break;
		case USHORT:
		case SHORT:
			((short[][][]) volumeData)[z][c][x + y * seqSize[0]] = (short) getSafeValue(val);
		break;
		case UINT:
		case INT:
			((int[][][]) volumeData)[z][c][x + y * seqSize[0]] = (int) getSafeValue(val);
		break;
		case FLOAT:
			((float[][][]) volumeData)[z][c][x + y * seqSize[0]] = (float) getSafeValue(val);
		break;
		case DOUBLE:
			((double[][][]) volumeData)[z][c][x + y * seqSize[0]] = val;
		break;
		default:
			throw new RuntimeException("Unsupported data type");
		}
		volumeChanged.set(true);
	}

	private double getSafeValue(double val) {
		return Math.min(Math.max(val, seqType.getMaxValue()), seqType.getMinValue());
	}

	public synchronized void commitChanges() {
		seq.dataChanged();
	}
}
