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
		setTileSize(new Dimension());
	}

	public Dimension getTileSize() {
		return tileSize;
	}

	public void setTileSize(Dimension tileSize) {
		this.tileSize.setSize(tileSize);
	}

	protected boolean isProviderPrepared() {
		return providerPrepared;
	}

	protected void setProviderPrepared(boolean prepared) {
		providerPrepared = prepared;
	}

	protected LociImporterPlugin getImporter() {
		return importer;
	}

	public void setImporter(LociImporterPlugin importer) {
		this.importer = importer;
	}

	@Override
	public IcyBufferedImage getTile(Point tileIndex) throws IOException {
		prepareProvider();
		setCurrentTileIndex(tileIndex);
		computeTileRectangle();
		IcyBufferedImage tileImage;
		try {
			tileImage = getImporter().getImage(0, 0, getCurrentTileRectangle(), 0, 0);
		} catch (UnsupportedFormatException e) {
			throw new IOException(e);
		}
		return tileImage;
	}

	protected Point getCurrentTileIndex() {
		return currentTileIndex;
	}

	protected void setCurrentTileIndex(Point tileIndex) {
		currentTileIndex = tileIndex;
	}

	protected void prepareProvider() throws IOException {
		if (!isProviderPrepared()) {
			if (getImporter() == null)
				throw new IOException("No importer specified");

			if (getTileSize() == null || getTileSize().width * getTileSize().height == 0) {
				try {
					setTileSize(new Dimension(getImporter().getTileWidth(0), getImporter().getTileHeight(0)));
					setMetatadata(getImporter().getOMEXMLMetaData());
				} catch (UnsupportedFormatException e) {
					throw new IOException(e);
				}
			}

			setProviderPrepared(true);
		}
	}

	protected OMEXMLMetadata getMetatadata() {
		return metatadata;
	}

	protected void setMetatadata(OMEXMLMetadata metatadata) {
		this.metatadata = metatadata;
	}

	protected void computeTileRectangle() {
		computeCurrentTilePosition();
		computeCurrentTileSize();
		setCurrentTileRectangle(new Rectangle(getCurrentTilePosition().x, getCurrentTilePosition().y,
				getCurrentTileSize().width, getCurrentTileSize().height));
	}

	protected Rectangle getCurrentTileRectangle() {
		return currentTileRectangle;
	}

	protected void setCurrentTileRectangle(Rectangle currentTileRectangle) {
		this.currentTileRectangle = currentTileRectangle;
	}

	protected void computeCurrentTilePosition() {
		if (getCurrentTilePosition() == null)
			setCurrentTilePosition(new Point());

		getCurrentTilePosition().setLocation(getCurrentTileIndex().x * getTileSize().width,
				getCurrentTileIndex().y * getTileSize().height);
	}

	protected Point getCurrentTilePosition() {
		return currentTilePosition;
	}

	protected void setCurrentTilePosition(Point currentTilePosition) {
		this.currentTilePosition = currentTilePosition;
	}

	protected void computeCurrentTileSize() {
		if (getCurrentTileSize() == null)
			setCurrentTileSize(new Dimension());

		getCurrentTileSize().setSize(getTileSize().width, getTileSize().height);

		if (getMetatadata().getPixelsSizeX(0).getValue() <= getCurrentTilePosition().x + getTileSize().width) {
			getCurrentTileSize().width = getMetatadata().getPixelsSizeX(0).getValue() - getCurrentTilePosition().x;
		}
		if (getMetatadata().getPixelsSizeY(0).getValue() <= getCurrentTilePosition().y + getTileSize().height) {
			getCurrentTileSize().height = getMetatadata().getPixelsSizeY(0).getValue() - getCurrentTilePosition().y;
		}
	}

	protected Dimension getCurrentTileSize() {
		return currentTileSize;
	}

	protected void setCurrentTileSize(Dimension currentTileSize) {
		this.currentTileSize = currentTileSize;
	}

}
