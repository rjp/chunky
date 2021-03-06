/* Copyright (c) 2010-2014 Jesper Öqvist <jesper@llbit.se>
 *
 * This file is part of Chunky.
 *
 * Chunky is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chunky is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Chunky.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.llbit.chunky.map;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;

import se.llbit.chunky.world.Chunk;
import se.llbit.chunky.world.Chunk.Renderer;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.ChunkView;
import se.llbit.chunky.world.listeners.ChunkUpdateListener;

/**
 * Keeps a buffered image of rendered chunks. Only re-render chunks when
 * they are not buffered. The buffer contains all visible chunks, plus some
 * outside of the view.
 *
 * @author Jesper Öqvist (jesper@llbit.se)
 */
public class MapBuffer implements ChunkUpdateListener, Iterable<ChunkPosition> {

	private BufferedImage buffer;
	private int buffW;
	private int buffH;
	private Chunk.Renderer buffMode;
	private int buffLayer;
	private ChunkView view;
	private int x_offset = 0;
	private int y_offset = 0;
	private int[] data;
	private Graphics graphics;
	private Set<ChunkPosition> updatedRegions = new HashSet<ChunkPosition>();
	private Set<ChunkPosition> updatedChunks = new HashSet<ChunkPosition>();

	/**
	 * Create a new render buffer for the provided view
	 * @param view
	 */
	public MapBuffer(ChunkView view) {
		this.view = view;
		initBuffer(view);
		redrawAllChunks(view);
	}

	private void initBuffer(ChunkView newView) {
		buffW = newView.chunkScale * (newView.px1 - newView.px0 + 1);
		buffH = newView.chunkScale * (newView.pz1 - newView.pz0 + 1);
		buffer = new BufferedImage(buffW, buffH,
				BufferedImage.TYPE_INT_RGB);
		graphics = buffer.getGraphics();
		DataBufferInt dataBuffer = (DataBufferInt) buffer.getRaster().getDataBuffer();
		data = dataBuffer.getData();
		graphics.setColor(java.awt.Color.white);
		graphics.fillRect(0, 0, buffW, buffH);

	}

	/**
	 * Force all visible chunks to be redrawn
	 */
	public synchronized void flushCache() {
		graphics.setColor(java.awt.Color.white);
		graphics.fillRect(0, 0, buffW, buffH);
		redrawAllChunks(view);
	}

	/**
	 * Called when this render buffer should buffer another view.
	 * @param newView
	 * @param renderer
	 * @param layer
	 */
	public synchronized void updateView(ChunkView newView, Renderer renderer,
			int layer) {

		boolean bufferedMode = buffMode == renderer
				&& buffMode.bufferValid(view, newView, buffLayer, layer);

		if (!bufferedMode || !newView.equals(view)) {

			BufferedImage prev = buffer;
			initBuffer(newView);

			int ix0 = view.chunkScale * (view.px0 - newView.px0);
			int iz0 = view.chunkScale * (view.pz0 - newView.pz0);

			if (newView.chunkScale == view.chunkScale) {
				graphics.drawImage(prev, ix0, iz0,
						newView.chunkScale * (view.px1 - view.px0 + 1),
						newView.chunkScale * (view.pz1 - view.pz0 + 1), null);
			} else {
				graphics.drawImage(prev, ix0, iz0, null);
			}

			DataBufferInt dataBuffer = (DataBufferInt) buffer.getRaster().getDataBuffer();
			data = dataBuffer.getData();

			if (!bufferedMode /*|| view.chunkScale != newView.chunkScale*/) {
				redrawAllChunks(newView);
			} else {
				redrawNewChunks(view, newView);
			}
		}

		buffMode = renderer;
		buffLayer = layer;

		view = newView;
		x_offset = (int) (view.scale * (view.px0 - view.x0));
		y_offset = (int) (view.scale * (view.pz0 - view.z0));
	}

	private synchronized void redrawAllChunks(ChunkView newView) {
		updatedRegions.clear();
		updatedChunks.clear();
		if (view.scale >= 16) {
			for (int x = newView.px0; x <= newView.px1; ++x) {
				for (int z = newView.pz0; z <= newView.pz1; ++z) {
					updatedChunks.add(ChunkPosition.get(x, z));
				}
			}
		} else {
			for (int x = newView.prx0; x <= newView.prx1; ++x) {
				for (int z = newView.prz0; z <= newView.prz1; ++z) {
					updatedRegions.add(ChunkPosition.get(x, z));
				}
			}
		}
	}

	private synchronized void redrawNewChunks(ChunkView prevView, ChunkView newView) {
		if (view.scale >= 16) {
			Set<ChunkPosition> updated = new HashSet<ChunkPosition>();
			for (ChunkPosition chunk: updatedChunks) {
				if (newView.isChunkVisible(chunk)) {
					updated.add(chunk);
				}
			}
			for (int x = newView.px0; x <= newView.px1; ++x) {
				for (int z = newView.pz0; z <= newView.pz1; ++z) {
					if (!prevView.isChunkVisible(x, z)) {
						updated.add(ChunkPosition.get(x, z));
					}
				}
			}
			updatedChunks = updated;
		} else {
			Set<ChunkPosition> updated = new HashSet<ChunkPosition>();
			for (ChunkPosition region: updatedRegions) {
				if (newView.isRegionVisible(region)) {
					updated.add(region);
				}
			}
			for (int x = newView.prx0; x <= newView.prx1; ++x) {
				for (int z = newView.prz0; z <= newView.prz1; ++z) {
					if (!prevView.isRegionFullyVisible(newView, x, z)) {
						updated.add(ChunkPosition.get(x, z));
					}
				}
			}
			updatedRegions = updated;
		}
	}

	/**
	 * Render the currently buffered view.
	 * @param g
	 */
	public final synchronized void renderBuffered(Graphics g) {
		renderBuffered1(g);
	}

	/**
	 * Debug method
	 * @param g
	 */
	public final synchronized void renderBuffered2(Graphics g) {
		if (buffer != null) {
			graphics.dispose();
			int margin = 50;
			double iw = view.chunkScale * (view.x1 - view.x0);
			double ih = view.chunkScale * (view.z1 - view.z0);
			double xscale = (view.width-margin*2) / iw;
			double yscale = (view.height-margin*2) / ih;
			g.setColor(java.awt.Color.gray);
			g.fillRect(0, 0, view.width, view.height);
			g.drawImage(buffer, (int) (margin + x_offset*xscale),
					(int) (margin + y_offset*yscale),
					(int) (buffW*xscale), (int) (buffH*yscale), null);
			g.setColor(java.awt.Color.red);
			g.drawRect(margin, margin, view.width-margin*2, view.height-margin*2);
			graphics = buffer.getGraphics();
		}
	}

	/**
	 * Default buffer rendering
	 * @param g
	 */
	public final synchronized void renderBuffered1(Graphics g) {
		if (buffer != null) {
			graphics.dispose();
			if (view.scale == view.chunkScale) {
				g.drawImage(buffer, x_offset, y_offset, null);
			} else {
				float scale = view.scale / (float) view.chunkScale;
				g.drawImage(buffer, x_offset, y_offset, (int) (buffW*scale), (int) (buffH*scale), null);
			}
			graphics = buffer.getGraphics();
		}
	}

	/**
	 * @return The graphics object for this buffer
	 */
	public synchronized Graphics getGraphics() {
		return graphics;
	}

	/**
	 * @return The width of the buffer
	 */
	public synchronized int getWidth() {
		return buffW;
	}

	/**
	 * @return The height of the buffer
	 */
	public synchronized int getHeight() {
		return buffH;
	}

	/**
	 * Set a pixel in the buffer to a specific color
	 * @param x
	 * @param y
	 * @param rgb
	 */
	public synchronized void setRGB(int x, int y, int rgb) {
		data[x + buffW * y] = rgb;
	}

	/**
	 * @param x
	 * @param y
	 * @return The pixel color at (x, y)
	 */
	public synchronized int getRGB(int x, int y) {
		return buffer.getRGB(x, y);
	}

	/**
	 * @return The buffered view
	 */
	public ChunkView getView() {
		return view;
	}

	/**
	 * Save the buffered view as a PNG image
	 * @param targetFile
	 */
	public synchronized void renderPng(File targetFile) {
		try {
			BufferedImage crop = new BufferedImage(view.width, view.height,
					BufferedImage.TYPE_INT_RGB);
			crop.getGraphics().drawImage(buffer, x_offset, y_offset, null);
			ImageIO.write(crop, "png", targetFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Fill rect
	 * @param x0
	 * @param y0
	 * @param w
	 * @param h
	 * @param rgb
	 */
	public void fillRect(int x0, int y0, int w, int h, int rgb) {
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				data[x0 + x + buffW * (y0 + y)] = rgb;
			}
		}
	}

	/**
	 * Fill rect with alpha blending
	 * @param x0
	 * @param y0
	 * @param w
	 * @param h
	 * @param rgb
	 */
	public void fillRectAlpha(int x0, int y0, int w, int h, int rgb) {
		float[] src = new float[4];
		float[] dst = new float[4];
		se.llbit.math.Color.getRGBAComponents(rgb, src);
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				se.llbit.math.Color.getRGBComponents(data[x0 + x + buffW * (y0 + y)], dst);
				dst[0] = dst[0] * (1-src[3]) + src[0] * src[3];
				dst[1] = dst[1] * (1-src[3]) + src[1] * src[3];
				dst[2] = dst[2] * (1-src[3]) + src[2] * src[3];
				dst[3] = 1;
				data[x0 + x + buffW * (y0 + y)] = se.llbit.math.Color.getRGB(dst);
			}
		}
	}

	@Override
	public synchronized void chunkUpdated(ChunkPosition chunk) {
		if (view.isChunkVisible(chunk)) {
			if (view.scale >= 16) {
				updatedChunks.add(chunk);
			} else {
				updatedRegions.add(chunk.getRegionPosition());
			}
		}
	}

	@Override
	public synchronized void regionUpdated(ChunkPosition region) {
		if (view.scale >= 16) {
			int x0 = region.x;
			int x1 = region.x*32 + 31;
			int z0 = region.z;
			int z1 = region.z*32 + 31;
			for (int cx = x0; cx <= x1; ++cx) {
				for (int cz = z0; cz <= z1; ++cz) {
					chunkUpdated(ChunkPosition.get(cx, cz));
				}
			}
		} else if (view.isRegionVisible(region)) {
			updatedRegions.add(region);
		}
	}

	/**
	 * @return <code>true</code> if any of the visible chunks need to
	 * be redrawn
	 */
	public synchronized boolean haveUpdatedChunks() {
		return !updatedRegions.isEmpty();
	}

	// TODO use separate iterators for regions and chunks
	/**
	 * @return an iterator over chunks that need to be redrawn
	 */
	@Override
	synchronized public Iterator<ChunkPosition> iterator() {
		final Set<ChunkPosition> regions = updatedRegions;
		final Set<ChunkPosition> chunks = updatedChunks;
		updatedRegions = new HashSet<ChunkPosition>();
		updatedChunks = new HashSet<ChunkPosition>();
		return new Iterator<ChunkPosition>() {
			private final ChunkView bounds;
			private ChunkPosition next = null;
			private int x;
			private int z;
			private ChunkPosition region;
			private boolean containsRegion;

			{
				bounds = view;
				x = bounds.px0;
				z = bounds.pz0;
				region = ChunkPosition.get(x>>5, z>>5);
				containsRegion = regions.contains(region);
				findNext();
			}

			private void findNext() {
				while (z <= bounds.pz1) {
					int cx = x;
					int cz = z;
					int rx = cx>>5;
					int rz = cz>>5;
					x += 1;
					if (x > bounds.px1) {
						x = bounds.px0;
						z += 1;
					}
					if (region.x != rx || region.z != rz) {
						region = ChunkPosition.get(rx, rz);
						containsRegion = regions.contains(region);
					}
					if (containsRegion) {
						next = ChunkPosition.get(cx, cz);
						return;
					} else {
						ChunkPosition chunk = ChunkPosition.get(cx, cz);
						if (chunks.contains(chunk)) {
							next = chunk;
							return;
						}
					}
				}
				next = null;
			}

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public ChunkPosition next() {
				ChunkPosition pos = next;
				findNext();
				return pos;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Force the given chunk to be redrawn
	 * @param cx
	 * @param cz
	 */
	public synchronized void updateChunk(int cx, int cz) {
		regionUpdated(ChunkPosition.get(cx>>5, cz>>5));
	}

	/**
	 * Update region continaing the given chunk.
	 * @param cx
	 * @param cz
	 */
	public synchronized void updateRegion(int cx, int cz) {
		int rx = cx >> 5;
		int rz = cz >> 5;
		updateChunks(rx*32, rx*32+31, rz*32, rz*32+31);
	}

	/**
	 * Force the chunks within a rectangle to be redrawn
	 * @param x0
	 * @param x1
	 * @param z0
	 * @param z1
	 */
	public synchronized void updateChunks(int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x += 32) {
			for (int z = z0; z <= z1; z += 32) {
				regionUpdated(ChunkPosition.get(x>>5, z>>5));
			}
		}
	}

}
