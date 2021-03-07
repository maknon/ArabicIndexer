package com.maknoon;

import javax.swing.table.*;
import java.util.Vector;

class UpdateTableModel extends AbstractTableModel
{
	// Columns Numbers
	public static final int INSTALL_COL = 0;
	public static final int DESCRIPTION_COL = 1;
	private static final int VERSION_COL = 2;
	private static final int SIZE_COL = 3;

	// Names of the columns
	private final String[] colNames = new String[4];

	// Types of the columns
	private final Class[] colTypes = {Boolean.class, String.class, String.class, String.class};

	//store the data
	private final Vector<UpdatesData> dataVector;

	// Constructor
	UpdateTableModel(Vector<UpdatesData> m_dataVector, String headerTitle_0, String headerTitle_1, String headerTitle_2, String headerTitle_3)
	{
		// Names of the columns
		colNames[0] = headerTitle_0;
		colNames[1] = headerTitle_1;
		colNames[2] = headerTitle_2;
		colNames[3] = headerTitle_3;

		// Store the data
		dataVector = m_dataVector;
	}

	/*
	 * Don't need to implement this method unless your table's
	 * editable.
	 */
	public boolean isCellEditable(int row, int col)
	{
		UpdatesData data = dataVector.elementAt(row);

		//Note that the data/cell address is constant,
		//no matter where the cell appears onscreen.
		return (col == INSTALL_COL && !data.disabled);
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
		final UpdatesData data = dataVector.elementAt(row);

		if (col == INSTALL_COL)
			data.installStatus = (Boolean) value;

		boolean enableUpdateButton = false;
		boolean enableExportWithBooksCheckBox = false;
		for (int i = 0; i < dataVector.size(); i++)
		{
			final UpdatesData d = dataVector.elementAt(i);
			if (d.installStatus)
			{
				enableUpdateButton = true;
				if (!Update.updatingFiles.elementAt(i).startsWith("http"))
				{
					enableExportWithBooksCheckBox = true;
					break;
				}
			}
		}

		Update.updateButton.setEnabled(enableUpdateButton);
		Update.exportWithBooksCheckBox.setEnabled(enableExportWithBooksCheckBox);
		Update.pathButton.setEnabled(enableExportWithBooksCheckBox);
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
		UpdatesData data = dataVector.elementAt(row);

		switch (col)
		{
			case VERSION_COL:
				return data.version;
			case DESCRIPTION_COL:
				return data.description;
			case SIZE_COL:
				return data.size;
			case INSTALL_COL:
				return data.installStatus;
		}
		return new Object();
	}
}