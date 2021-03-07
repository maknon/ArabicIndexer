package com.maknoon;

import javax.swing.*;

class ResultsData
{
	public String path, name, parent, author, category;
    public ImageIcon type;

	ResultsData(ImageIcon bookType, String bookPath, String bookName, String bookParent, String bookAuthor, String bookCategory)
	{
        type = bookType;
		path = bookPath;
		name = bookName;
		parent = bookParent;
        author = bookAuthor;
        category = bookCategory;
	}
}