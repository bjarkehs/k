package org.kframework.krun.gui.UIDesign;

import java.awt.Color;

import org.kframework.kil.Cell;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.krun.gui.UIDesign.xmlEditor.ColorTagMap;
import org.kframework.utils.ColorUtil;

public class ColorVisitor extends BasicVisitor{
	public ColorVisitor(Context definitionHelper) {
		super(definitionHelper);
	}

	@Override
	public void visit(Cell cell) {
		Cell declaredCell = context.cells.get(cell.getLabel());
		if (declaredCell != null) {
			String declaredColor = declaredCell.getCellAttributes().get("color");
			if (declaredColor != null) {
				Color c = ColorUtil.colors.get(declaredColor);
				ColorTagMap.addColorToTag(cell.getLabel(), c);
			}
		}
		cell.getContents().accept(this);
	}
}
