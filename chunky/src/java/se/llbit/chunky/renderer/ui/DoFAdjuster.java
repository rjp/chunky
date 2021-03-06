/* Copyright (c) 2013-2014 Jesper Öqvist <jesper@llbit.se>
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
package se.llbit.chunky.renderer.ui;

import java.text.ParseException;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;

import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.ui.Adjuster;

/**
 * Adjuster specialized for DoF.
 * @author Jesper Öqvist <jesper@llbit.se>
 */
public class DoFAdjuster extends Adjuster {

	private final RenderManager renderMan;

	/**
	 * @param renderManager
	 */
	public DoFAdjuster(RenderManager renderManager) {
		super("Depth of Field", "Depth of Field",
				Camera.MIN_DOF, Camera.MAX_DOF);
		this.renderMan = renderManager;
		setLogarithmicMode();
		setClampMax(false);
	}

	@Override
	protected double parseText(String text) throws ParseException {
		if (text.equalsIgnoreCase("inf") || text.equalsIgnoreCase("Infinite") ||
				text.equalsIgnoreCase("Infinity")) {
			return Double.POSITIVE_INFINITY;
		} else {
			return super.parseText(text);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		JSlider source = (JSlider) e.getSource();
		if (source.getValue() == source.getMaximum()) {
			setTextFieldText("Infinity");
			renderMan.scene().camera().setDof(Double.POSITIVE_INFINITY);
		} else {
			super.stateChanged(e);
		}
	}

	@Override
	public void valueChanged(double newValue) {
		renderMan.scene().camera().setDof(newValue);
	}

	@Override
	public void update() {
		Camera camera = renderMan.scene().camera();
		if (camera.infiniteDoF()) {
			setTextFieldText("inf");
			setSliderValue(getSlider().getMaximum());
		} else {
			set(renderMan.scene().camera().getDof());
		}
	}
}
