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
package impl.danyfel80.sequence.volume.util;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.sequence.VolumetricImage;
import icy.type.DataType;
import icy.type.TypeUtil;

/**
 * @author Daniel Felipe Gonzalez Obando
 */
public class VolumetricImageCursor {

	VolumetricImage	vol;
	int[]						volSize;
	DataType				volType;

	AtomicBoolean volumeChanged;

	Object volumeData;

	/**
	 * 
	 */
	public VolumetricImageCursor(VolumetricImage vol) {
		this.vol = vol;
		IcyBufferedImage firstPlane = vol.getFirstImage();
		this.volSize = new int[] { firstPlane.getSizeX(), firstPlane.getSizeY(), vol.getImages().size() };
		this.volType = firstPlane.getDataType_();
		Object volumeData;
		switch (volType) {
		case UBYTE:
		case BYTE:
			volumeData = new byte[vol.getImages().size()][][];
			break;
		case USHORT:
		case SHORT:
			volumeData = new short[vol.getImages().size()][][];
			break;
		case UINT:
		case INT:
			volumeData = new int[vol.getImages().size()][][];
			break;
		case FLOAT:
			volumeData = new float[vol.getImages().size()][][];
			break;
		case DOUBLE:
			volumeData = new double[vol.getImages().size()][][];
			break;
		default:
			throw new RuntimeException("Unsupported data type: " + volType);
		}
		
		int i = 0;
		for (Iterator<IcyBufferedImage> iterator = vol.getImages().values().iterator(); iterator.hasNext();) {
			IcyBufferedImage plane = iterator.next();
			switch (volType) {
			case UBYTE:
			case BYTE:
				((byte[][][])volumeData)[i++] = (byte[][]) plane.getDataXYC();
				break;
			case USHORT:
			case SHORT:
				((short[][][])volumeData)[i++] = (short[][]) plane.getDataXYC();
				break;
			case UINT:
			case INT:
				((int[][][])volumeData)[i++] = (int[][]) plane.getDataXYC();
				break;
			case FLOAT:
				((float[][][])volumeData)[i++] = (float[][]) plane.getDataXYC();
				break;
			case DOUBLE:
				((double[][][])volumeData)[i++] = (double[][]) plane.getDataXYC();
				break;
			default:
				throw new RuntimeException("Unsupported data type: " + volType);
			}
			
		}
		this.volumeData = volumeData;
		volumeChanged = new AtomicBoolean(false);
	}

	public VolumetricImageCursor(Sequence seq, int t) {
		this(seq.getVolumetricImage(t));
	}

	public synchronized double get(int x, int y, int z, int c) {

		switch (volType) {
		case UBYTE:
		case BYTE:
			return TypeUtil.toDouble(((byte[][][]) volumeData)[z][c][x + y * volSize[0]], volType.isSigned());
		case USHORT:
		case SHORT:
			return TypeUtil.toDouble(((short[][][]) volumeData)[z][c][x + y * volSize[0]], volType.isSigned());
		case UINT:
		case INT:
			return TypeUtil.toDouble(((int[][][]) volumeData)[z][c][x + y * volSize[0]], volType.isSigned());
		case FLOAT:
			return ((float[][][]) volumeData)[z][c][x + y * volSize[0]];
		case DOUBLE:
			return ((double[][][]) volumeData)[z][c][x + y * volSize[0]];
		default:
			throw new RuntimeException("Unsupported data type: " + volType);
		}
	}

	public synchronized void set(int x, int y, int z, int c, double val) {

		switch (volType) {
		case UBYTE:
		case BYTE:
			((byte[][][]) volumeData)[z][c][x + y * volSize[0]] = (byte) val;
		break;
		case USHORT:
		case SHORT:
			((short[][][]) volumeData)[z][c][x + y * volSize[0]] = (short) val;
		break;
		case UINT:
		case INT:
			((int[][][]) volumeData)[z][c][x + y * volSize[0]] = (int) val;
		break;
		case FLOAT:
			((float[][][]) volumeData)[z][c][x + y * volSize[0]] = (float) val;
		break;
		case DOUBLE:
			((double[][][]) volumeData)[z][c][x + y * volSize[0]] = val;
		break;
		default:
			throw new RuntimeException("Unsupported data type");
		}
		volumeChanged.set(true);
	}

	public synchronized void setSafe(int x, int y, int z, int c, double val) {

		switch (volType) {
		case UBYTE:
		case BYTE:
			((byte[][][]) volumeData)[z][c][x + y * volSize[0]] = (byte) getSafeValue(val);
		break;
		case USHORT:
		case SHORT:
			((short[][][]) volumeData)[z][c][x + y * volSize[0]] = (short) getSafeValue(val);
		break;
		case UINT:
		case INT:
			((int[][][]) volumeData)[z][c][x + y * volSize[0]] = (int) getSafeValue(val);
		break;
		case FLOAT:
			((float[][][]) volumeData)[z][c][x + y * volSize[0]] = (float) getSafeValue(val);
		break;
		case DOUBLE:
			((double[][][]) volumeData)[z][c][x + y * volSize[0]] = val;
		break;
		default:
			throw new RuntimeException("Unsupported data type");
		}
		volumeChanged.set(true);
	}

	private double getSafeValue(double val) {
		return Math.min(Math.max(val, volType.getMaxValue()), volType.getMinValue());
	}

	public synchronized void commitChanges() {
		for (IcyBufferedImage plane: vol.getImages().values()) {
			plane.dataChanged();
		}
	}

}
