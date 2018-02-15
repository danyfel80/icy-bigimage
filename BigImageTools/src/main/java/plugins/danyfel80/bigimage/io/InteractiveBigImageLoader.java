package plugins.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import algorithms.danyfel80.io.image.big.BigImageImporter;
import algorithms.danyfel80.io.image.big.BigImageUtil;
import icy.common.exception.UnsupportedFormatException;
import icy.sequence.Sequence;
import ome.units.quantity.Length;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

public class InteractiveBigImageLoader extends EzPlug {

	private EzVarFile inFile;
	private EzVarSequence inPreviewSequence;

	@Override
	protected void initialize() {
		inFile = new EzVarFile("Input File", null);
		inPreviewSequence = new EzVarSequence("Preview sequence");
		inPreviewSequence.setNoSequenceSelection();

		addEzComponent(inFile);
		addEzComponent(inPreviewSequence);
	}

	@Override
	protected void execute() {
		Sequence previewSequence = inPreviewSequence.getValue();
		File file = inFile.getValue(true);
		Length[] pixelSize;
		try {
			pixelSize = BigImageUtil.getImagePixelSize(file);
		} catch (UnsupportedFormatException | IOException e) {
			throw new RuntimeException(e);
		}

		Rectangle2D regionToLoad;
		if (previewSequence == null) {
			Dimension imDim;
			try {
				imDim = BigImageUtil.getImageDimension(file);
			} catch (IOException | UnsupportedFormatException e) {
				throw new RuntimeException(e);
			}
			regionToLoad = new Rectangle2D.Double(0, 0, imDim.width * pixelSize[0].value().doubleValue(),
					imDim.height * pixelSize[1].value().doubleValue());
		} else {
			List<ROI2DRectangle> rects = previewSequence.getROIs(ROI2DRectangle.class, true);
			double posX = previewSequence.getPositionX();
			double posY = previewSequence.getPositionY();

			if (rects.isEmpty()) {
				System.err.println("No selection to load, taking full image.");
				Dimension imDim;
				try {
					imDim = BigImageUtil.getImageDimension(file);
				} catch (IOException | UnsupportedFormatException e) {
					throw new RuntimeException(e);
				}
				regionToLoad = new Rectangle2D.Double(0, 0, imDim.width * pixelSize[0].value().doubleValue(),
						imDim.height * pixelSize[1].value().doubleValue());
			} else {
				Rectangle rect = rects.get(0).getBounds();
				regionToLoad = new Rectangle2D.Double(posX + previewSequence.getPixelSizeX() * rect.x,
						posY + previewSequence.getPixelSizeY() * rect.y, previewSequence.getPixelSizeX() * rect.width,
						previewSequence.getPixelSizeY() * rect.height);
			}
		}

		Rectangle regionToLoadPx = new Rectangle((int) (regionToLoad.getX() / pixelSize[0].value().doubleValue()),
				(int) (regionToLoad.getY() / pixelSize[1].value().doubleValue()),
				(int) (regionToLoad.getWidth() / pixelSize[0].value().doubleValue()),
				(int) (regionToLoad.getHeight() / pixelSize[1].value().doubleValue()));

		Dimension loadedDimension = new Dimension(regionToLoadPx.getSize());
		int maxWidth = 2000;
		int maxHeight = 2000;

		int resolution = 0;
		double[] loadedPixelSize = new double[] { pixelSize[0].value().doubleValue(), pixelSize[1].value().doubleValue() };
		while (loadedDimension.width > maxWidth || loadedDimension.height > maxHeight) {
			resolution++;
			loadedDimension.width /= 2;
			loadedDimension.height /= 2;
			loadedPixelSize[0] *= 2d;
			loadedPixelSize[1] *= 2d;
		}

		BigImageImporter importer = new BigImageImporter(file, resolution, regionToLoadPx);
		importer.setProgressListener((progress, msg, data) -> {
			getUI().setProgressBarValue(progress);
			getUI().setProgressBarMessage("Loading preview (" + msg + ")");
			return false;
		});

		try {
			Sequence seq = importer.call();
			addSequence(seq);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void clean() {
	}

}
