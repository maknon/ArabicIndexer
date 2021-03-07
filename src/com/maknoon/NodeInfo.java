package com.maknoon;

class NodeInfo
{
	public final String name;
	public final String path;
	public final String category;
	public final String parent;
    public final String author;
    public final int id;
    public final String absolutePath; // Version 1.9

	NodeInfo(final String bookName, final String bookParent, final String bookPath, final String bookCategory, final String bookAuthor, final String bookAbsolutePath, final int bookId)
	{
		name = bookName;
		path = bookPath;
		parent = bookParent;
		category = bookCategory;
		author = bookAuthor;
		absolutePath = bookAbsolutePath;
		id = bookId;
	}

	public String toString() {return name;}
}