package algorithms.danyfel80.io.sequence.tileprovider;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;

import icy.common.exception.UnsupportedFormatException;
import icy.image.IcyBufferedImage;
import icy.plugin.PluginLoader;
import ome.xml.meta.OMEXMLMetadata;
import plugins.kernel.importer.LociImporterPlugin;

public class CachedLargeSequenceTileProvider implements ITileProvider, AutoCloseable {
	private static CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
			.withClassLoader(PluginLoader.getLoader()).build(true);

	public static class Builder {
		private LociImporterPlugin importer;
		private Dimension tileSize;

		CachedLargeSequenceTileProvider tileProvider;

		public Builder(LociImporterPlugin importer) throws IllegalArgumentException {
			if (importer == null)
				throw new IllegalArgumentException("Null importer specified");
			this.importer = importer;
		}

		public Builder tileSize(Dimension tileSize) {
			this.tileSize = tileSize;
			return this;
		}

		public CachedLargeSequenceTileProvider build() throws IOException {
			tileProvider = new CachedLargeSequenceTileProvider();
			tileProvider.setImporter(importer);
			if (tileSize == null) {
				try {
					tileSize = new Dimension(importer.getTileWidth(0), importer.getTileHeight(0));
				} catch (UnsupportedFormatException | IOException e) {
					throw new IOException("Could not retrieve the tile size", e);
				}
			}
			tileProvider.setTileSize(tileSize);
			return tileProvider;
		}
	}

	private LociImporterPlugin importer;
	private OMEXMLMetadata metadata;
	private Dimension tileSize;
	private final String cacheName = "TileProviderCache" + this.hashCode();
	private Cache<Point, IcyBufferedImage> tileCache;
	private boolean providerPrepared;

	private Point currentTileIndex;
	private Point currentTilePosition;
	private Dimension currentTileSize;
	private Rectangle currentTileRectangle;

	protected void setImporter(LociImporterPlugin importer) {
		this.importer = importer;
	}
	
	public Dimension getTileSize() {
		return this.tileSize;
	}

	protected void setTileSize(Dimension tileSize) {
		this.tileSize = tileSize;
	}

	@Override
	public IcyBufferedImage getTile(Point tileIndex) throws IOException, IllegalArgumentException {
		prepareProvider();
		setCurrentTileIndex(tileIndex);
		return retrieveCurrentTile();
	}

	private void prepareProvider() throws IOException {
		if (!isProviderPrepared()) {
			startCache();
			try {
				metadata = importer.getOMEXMLMetaData();
			} catch (UnsupportedFormatException | IOException e) {
				throw new IOException("Could not retrieve the metadata", e);
			}
			setProviderPrepared();
		}
	}

	private boolean isProviderPrepared() {
		return providerPrepared;
	}

	private void setProviderPrepared() {
		providerPrepared = true;
	}

	private void startCache() {
		this.tileCache = cacheManager.getCache(cacheName, Point.class, IcyBufferedImage.class);
		if (tileCache == null) {
			tileCache = cacheManager.createCache(cacheName, CacheConfigurationBuilder
					.newCacheConfigurationBuilder(Point.class, IcyBufferedImage.class, ResourcePoolsBuilder.heap(500)).build());
		}
	}

	private Point getCurrentTileIndex() {
		return currentTileIndex;
	}

	private void setCurrentTileIndex(Point tileIndex) throws IllegalArgumentException {
		if (tileIndex == null)
			throw new IllegalArgumentException("Null tile index");
		currentTileIndex = tileIndex;
	}

	private IcyBufferedImage retrieveCurrentTile() throws IOException {
		IcyBufferedImage currentTile = retrieveCurrentTileFromCache();
		if (currentTile == null) {
			currentTile = computeCurrentTile();
			storeCurrentTileInCache(currentTile);
		}
		return currentTile;
	}

	private IcyBufferedImage retrieveCurrentTileFromCache() {
		return tileCache.get(getCurrentTileIndex());
	}

	private IcyBufferedImage computeCurrentTile() throws IOException {
		computeTileRectangle();
		IcyBufferedImage tileImage;
		try {
			tileImage = importer.getImage(0, 0, currentTileRectangle, 0, 0);
		} catch (UnsupportedFormatException | IOException e) {
			throw new IOException(String.format("Could not get the tile image (%s)", currentTileRectangle), e);
		}
		return tileImage;
	}

	private void computeTileRectangle() {
		computeCurrentTilePosition();
		computeCurrentTileSize();
		currentTileRectangle = new Rectangle(currentTilePosition.x, currentTilePosition.y, currentTileSize.width,
				currentTileSize.height);
	}

	private void computeCurrentTilePosition() {
		if (currentTilePosition == null)
			currentTilePosition = new Point();

		currentTilePosition.setLocation(getCurrentTileIndex().x * tileSize.width,
				getCurrentTileIndex().y * tileSize.height);
	}

	private void computeCurrentTileSize() {
		if (currentTileSize == null)
			currentTileSize = new Dimension();

		currentTileSize.setSize(tileSize.width, tileSize.height);

		if (metadata.getPixelsSizeX(0).getValue() <= currentTilePosition.x + tileSize.width) {
			currentTileSize.width = metadata.getPixelsSizeX(0).getValue() - currentTilePosition.x;
		}
		if (metadata.getPixelsSizeY(0).getValue() <= currentTilePosition.y + tileSize.height) {
			currentTileSize.height = metadata.getPixelsSizeY(0).getValue() - currentTilePosition.y;
		}
	}

	private void storeCurrentTileInCache(IcyBufferedImage currentTileImage) {
		tileCache.put(currentTileIndex, currentTileImage);
	}

	@Override
	public void close() throws Exception {
		cacheManager.removeCache(cacheName);
	}

}
