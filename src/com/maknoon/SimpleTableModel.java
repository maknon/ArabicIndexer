package com.maknoon;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.Vector;

class SimpleTableModel extends AbstractTableModel
{
	//Columns Number.
	public static final int TYPE_COL = 0;
	public static final int NAME_COL = 1;
	public static final int PARENT_NAME_COL = 2;// In the case of the book contains many volumes.
	public static final int AUTHOR_COL = 3; // Version 1.6
	public static final int CATEGORY_COL = 4; // Version 1.6
	public static final int PATH_COL = 5;

	//Names of the columns.
	private final String[] colNames = new String[6];

	// Types of the columns.
	private final Class[] colTypes = {ImageIcon.class, String.class, String.class, String.class, String.class, String.class};

	//store the data
	private final Vector<ResultsData> dataVector;

	private final boolean disablePARENT_NAME_COL, disableAUTHOR_COL, disableCATEGORY_COL;

	SimpleTableModel(Vector<ResultsData> m_dataVector, String headerTitle_0, String headerTitle_1, String headerTitle_2, String headerTitle_3, String headerTitle_4, String headerTitle_5, boolean disableName, boolean disableAuthor, boolean disableCategory)
	{
		//Names of the columns
		colNames[0] = headerTitle_0;
		colNames[1] = headerTitle_1;
		colNames[2] = headerTitle_2;
		colNames[3] = headerTitle_3;
		colNames[4] = headerTitle_4;
		colNames[5] = headerTitle_5;

		//store the data
		dataVector = m_dataVector;

		// To disable the NAME_COL in case of adding to a book which consists of multi-volumes.
		disablePARENT_NAME_COL = disableName;
		disableAUTHOR_COL = disableAuthor;
		disableCATEGORY_COL = disableCategory;
	}

	/*
	 * Don't need to implement this method unless your table's
	 * editable.
	 */
	public boolean isCellEditable(int row, int col)
	{
		//Note that the data/cell address is constant,
		//no matter where the cell appears onscreen.

		if (col == PATH_COL || col == TYPE_COL)// i.e. disable PATH_COL/TYPE_COL
			return false;
		else
		{
			if (disablePARENT_NAME_COL && col == PARENT_NAME_COL)
				return false;
			else
			{
				if (disableAUTHOR_COL && col == AUTHOR_COL)
					return false;
				else
				{
					if (disableCATEGORY_COL && col == CATEGORY_COL)
						return false;
					else
						return true;
				}
			}
		}
	}

	/**
	 * getColumnCount
	 * Number columns same as the column array length
	 */
	public int getColumnCount()
	{
		return colNames.length;
	}

	/**
	 * getRowCount
	 * Row count same as the size of data vector
	 */
	public int getRowCount()
	{
		return dataVector.size();
	}

	/**
	 * setValueAt
	 * This function updates the data in the TableModel
	 * depending upon the change in the JTable
	 */
	public void setValueAt(Object value, int row, int col)
	{
		final ResultsData data = dataVector.elementAt(row);
		switch (col)
		{
			case PATH_COL:
				data.path = (String) value;
				break;
			case NAME_COL:
				data.name = (String) value;
				break;
			case PARENT_NAME_COL:
				data.parent = (String) value;
				break;
			case AUTHOR_COL:
				data.author = (String) value;
				break;
			case CATEGORY_COL:
				data.category = (String) value;
				break;
			case TYPE_COL:
				data.type = (ImageIcon) value;
				break;
		}
	}

	// Version 1.6
	public void removeRow(int row)
	{
		dataVector.remove(row);
		fireTableRowsDeleted(row, row);
	}

	/*
	 * JTable uses this method to determine the default renderer/
	 * editor for each cell.  If we didn't implement this method,
	 * then the last column would contain text ("true"/"false"),
	 * rather than a check box.
	 */
	//public Class getColumnClass(int c) {return getValueAt(0, c).getClass();}

	public String getColumnName(int col)
	{
		return colNames[col];
	}

	public Class getColumnClass(int col)
	{
		return colTypes[col];
	}

	/**
	 * getValueAt
	 * This function updates the JTable depending upon the
	 * data in the TableModel
	 */
	public Object getValueAt(int row, int col)
	{
		final ResultsData data = dataVector.elementAt(row);
		switch (col)
		{
			case PATH_COL:
				return data.path;
			case NAME_COL:
				return data.name;
			case PARENT_NAME_COL:
				return data.parent;
			case AUTHOR_COL:
				return data.author;
			case CATEGORY_COL:
				return data.category;
			case TYPE_COL:
				return data.type;
			default:
				return new Object();
		}
	}
}