package plugins.danyfel80.bigimage.io;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.vecmath.Point3d;

import algorithms.danyfel80.bigimage.io.BigImageWriter;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import ome.units.UNITS;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarFile;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;

/**
 * This class allows to save a big image using tiles.
 * @author Daniel Felipe Gonzalez Obando
 */
public class SaveBigImage extends EzPlug implements EzStoppable {
	
	private EzVarSequence inSeq = new EzVarSequence("Sequence");
	private EzVarFile inFile = new EzVarFile("File", "");
	private EzVarInteger inTileSize = new EzVarInteger("Tile size");
	
	private IcyBufferedImage image;
	private BigImageWriter saver;

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#initialize()
	 */
	@Override
	protected void initialize() {
		addEzComponent(inSeq);
		addEzComponent(inFile);
		addEzComponent(inTileSize);
	}
	
	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#execute()
	 */
	@Override
	protected void execute() {
		this.image = inSeq.getValue().getFirstImage();
		
		File file = inFile.getValue();
		int tileSize = inTileSize.getValue();
		int tileSizeX = tileSize;
		int tileSizeY = tileSize;

		this.getUI().setProgressBarMessage("Saving");

		int x, y;
		int w, h;
		int sizeX, sizeY;
		int n, m;
		int diffWidth, diffHeight;

		sizeX = image.getWidth();
		sizeY = image.getHeight();
		if (tileSizeX <= 0)
			tileSizeX = sizeX;
		if (tileSizeY <= 0)
			tileSizeY = sizeY;
		n = sizeX / tileSizeX;
		m = sizeY / tileSizeY;
		if (n == 0) {
			tileSizeX = sizeX;
			n = 1;
		}
		if (m == 0) {
			tileSizeY = sizeY;
			m = 1;
		}
		diffWidth = sizeX - n * tileSizeX;
		diffHeight = sizeY - m * tileSizeY;
		if (diffWidth > 0)
			n++;
		if (diffHeight > 0)
			m++;
		
		Point3d pxSize = new Point3d();
		pxSize.x = inSeq.getValue().getMetadata().getPixelsPhysicalSizeX(0).value(UNITS.MICROMETER).doubleValue();
		pxSize.y = inSeq.getValue().getMetadata().getPixelsPhysicalSizeY(0).value(UNITS.MICROMETER).doubleValue();
		pxSize.z = inSeq.getValue().getMetadata().getPixelsPhysicalSizeZ(0).value(UNITS.MICROMETER).doubleValue();
		
		saver = null;
		try {
			saver = new BigImageWriter(file, new Dimension(image.getWidth(), image.getHeight()), pxSize, image.getSizeC(),
					image.getDataType_(), new Dimension(tileSizeX, tileSizeY));
		} catch (ServiceException | FormatException | IOException e1) {
			e1.printStackTrace();
			return;
		}
		
		
			ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()-1);
			for (int i = 0; i < m; i++) {
				if (diffHeight > 0 && i == (m - 1)) {
					y = sizeY - diffHeight;
					h = diffHeight;
				} else {
					y = tileSizeY * i;
					h = tileSizeY;
				}
				for (int j = 0; j < n; j++) {
					if (diffWidth > 0 && j == (n - 1)) {
						x = sizeX - diffWidth;
						w = diffWidth;
					} else {
						x = tileSizeX * j;
						w = tileSizeX;
					}
					
					Rectangle rect = new Rectangle(x, y, w, h);
					Point point = new Point(x, y);
					threadPool.submit(new SavingTask(rect, point));
				}
			}

			threadPool.shutdown();
			try {
				threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				return;
			} finally {
				try {
					saver.closeWriter();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	}

	/* (non-Javadoc)
	 * @see plugins.adufour.ezplug.EzPlug#clean()
	 */
	@Override
	public void clean() {
		// TODO Auto-generated method stub

	}

	class SavingTask implements Runnable {

		private final Rectangle rect;
		private final Point point;
		
		public SavingTask(Rectangle rect, Point point) {
			this.rect = rect;
			this.point = point;
		}

		@Override
		public void run() {
			try {
				System.out.println("Saving tile " + rect + " at " + point);
				saver.saveTile(IcyBufferedImageUtil.getSubImage(image, rect), point);
			} catch (FormatException | IOException e) {
				e.printStackTrace();
			}
		}
		
	}

}
