package algorithms.danyfel80.io.sequence.tileprovider;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;

import icy.common.exception.UnsupportedFormatException;
import icy.image.IcyBufferedImage;
import ome.xml.meta.OMEXMLMetadata;
import plugins.kernel.importer.LociImporterPlugin;

public class LargeSequenceTileProvider implements ITileProvider {

	private Dimension tileSize;
	private boolean providerPrepared;
	private LociImporterPlugin importer;

	private Rectangle currentTileRectangle;
	private Point currentTileIndex;
	private OMEXMLMetadata metatadata;
	private Point currentTilePosition;
	private Dimension currentTileSize;

	public LargeSequenceTileProvider() {
		tileSize = new Dimension();
	}

	private LociImporterPlugin getImporter() {
		return importer;
	}

	public void setImporter(LociImporterPlugin importer) {
		this.importer = importer;
	}

	@Override
	public IcyBufferedImage getTile(Point tileIndex) throws IOException {
		prepareProvider();
		currentTileIndex = tileIndex;
		getTileRectangle();
		IcyBufferedImage tileImage;
		try {
			tileImage = importer.getImage(0, 0, currentTileRectangle, 0, 0);
		} catch (UnsupportedFormatException e) {
			throw new IOException(e);
		}
		return tileImage;
	}

	private void prepareProvider() throws IOException {
		if (!isProviderPrepared()) {
			if (getImporter() == null)
				throw new IOException("No importer specified");

			if (tileSize.width * tileSize.height == 0) {
				try {
					setTileSize(new Dimension(importer.getTileWidth(0), importer.getTileHeight(0)));
					metatadata = importer.getOMEXMLMetaData();
				} catch (UnsupportedFormatException e) {
					throw new IOException(e);
				}
			}

			setProviderPrepared(true);
		}
	}

	private void getTileRectangle() {
		getCurrentTilePosition();
		getCurrentTileSize();
		currentTileRectangle = new Rectangle(currentTilePosition.x, currentTilePosition.y, currentTileSize.width,
				currentTileSize.height);
	}

	private void getCurrentTilePosition() {
		if (currentTilePosition == null)
			currentTilePosition = new Point();

		currentTilePosition.setLocation(currentTileIndex.x * tileSize.width, currentTileIndex.y * tileSize.height);
	}

	private void getCurrentTileSize() {
		if (currentTileSize == null)
			currentTileSize = new Dimension();

		currentTileSize.width = tileSize.width;
		currentTileSize.height = tileSize.height;

		if (metatadata.getPixelsSizeX(0).getValue() <= currentTilePosition.x + tileSize.width) {
			currentTileSize.width = metatadata.getPixelsSizeX(0).getValue() - currentTilePosition.x;
		}
		if (metatadata.getPixelsSizeY(0).getValue() <= currentTilePosition.y + tileSize.height) {
			currentTileSize.height = metatadata.getPixelsSizeY(0).getValue() - currentTilePosition.y;
		}
	}

	private boolean isProviderPrepared() {
		return providerPrepared;
	}

	private void setProviderPrepared(boolean prepared) {
		providerPrepared = prepared;
	}

	public void setTileSize(Dimension tileSize) {
		this.tileSize.setSize(tileSize);
	}

}
