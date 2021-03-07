package com.maknoon;

/*
 * Maknoon Manuscripts Indexer
 * Version 2.1
 */
import javax.swing.*;
import java.awt.*;
import javax.swing.border.TitledBorder;
import java.awt.event.*;
import javax.swing.table.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.util.List;
import java.util.zip.*;
import java.sql.*;
import java.net.*;
import javax.swing.filechooser.*;
import javax.swing.undo.*;
import java.awt.datatransfer.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

import com.alee.extended.label.WebStyledLabel;
import com.alee.extended.tree.CheckStateChange;
import com.alee.extended.tree.CheckStateChangeListener;
import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.checkbox.CheckState;
import com.alee.laf.menu.WebPopupMenu;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.*;
import org.apache.lucene.store.*;
import org.apache.lucene.index.IndexWriterConfig.*;
import org.apache.lucene.search.highlight.*;

public class ArabicIndexer extends JFrame
{
	static final private Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
	static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
	private final JTree tree, authorTree; // Version 1.7, Add Author Tree
	static String bookTableName;
	private final Vector<String> searchResultsPathVector = new Vector<>();
	private final Vector<String> searchResultsPageVector = new Vector<>();
	private final Vector<String> searchResultsIdVector = new Vector<>();
	private final JList<String> searchResultsList;
	private boolean searchThreadWork = false;
	JButton orderButton, saveButton, backButton, forwardButton, viewButton; // Version 1.6
	private final DefaultMutableTreeNode treeRoot, searchRoot, authorTreeRoot, authorSearchRoot; // Version 1.5, searchRoot is used to display the new search choices (search range)
	// Version 1.6, Replace foxitReader with SumatraPDF

	// Version 1.3
	static final boolean isMAC = System.getProperty("os.name").equals("Mac OS X");
	//static boolean isLinux = System.getProperty("os.name").equals("Linux")?true:false;

	// Version 1.4, list model for the displayed history search entries.
	private final DefaultListModel<String> displayedHistoryListModel = new DefaultListModel<>();

	// Version 1.4, make it global to allow it in the shutdown()
	private final DefaultListModel<String> historyListModel = new DefaultListModel<>();

	// Version 1.1, this label is used within the JOptionPane for the adding books to indicate that the indexing files are within creation process.
	//JLabel creatingIndexingFilesLabel = new JLabel();

	// Global ArabicAnalyzer to speed the process time.
	private ArabicAnalyzer arabicAnalyzer;

	// Version 1.5
	private org.apache.lucene.analysis.ar.ArabicAnalyzer arabicLuceneAnalyzer;
	private ArabicRootAnalyzer arabicRootsAnalyzer;

	// Version 1.4, Opening global Searcher for performance gain [http://wiki.apache.org/lucene-java/ImproveSearchingSpeed].
	// Version 1.5, Add arabicRootsTableSearcher, arabicRootsSearcher, arabicLuceneSearcher
	static IndexSearcher defaultSearcher, arabicRootsSearcher, arabicLuceneSearcher;

	// Version 1.5, Global IndexWriter to get rid of all lock exceptions.
	static IndexWriter indexWriter, arabicRootsWriter, arabicLuceneWriter;

	// Version 1.7
	enum lang
	{Arabic, Urdu, English}

	static lang language = lang.Arabic;

	// Version 1.4, Shared DB Connection for performance increase
	Connection sharedDBConnection;

	private String bookName = "", bookPath = "", bookAbsolutePath = "", bookCategory = "", bookAuthor = "", addType = "", bookParentName = "";
	private boolean leafNode, rootNode; // i.e. all of them = false.
	private int bookId = 0;

	// Version 1.7, To know which tree is being selected.
	boolean authorTreeSelected = false;

	private int searchResultSelectedIndex = -1;

	// Version 1.6, To re-select the same search list index
	private boolean fireSearchListSelectionListener;

	// Version 1.6
	private final JEditorPane displayEditTextPane; // Version 1.7, JEditorPane instead of JTextPane since it is easier in setting the font in HTML mode. In addition, it solved the problems of not rendering HTML in some cases (especially with urdu).
	private final JTextField pageTextField, pagesTextField;
	private String currentDisplayedPage = ""; // To enable revert back to the current displayed page when editing -with mistake- pageTextField manually by the user

	// Version 1.7, to store the search text which will be used when selecting any list item.
	String currentSearchText = "";

	// Version 1.7, To indicate the used arabic search Type for the displayed results.
	enum searchType
	{DEFAULT, ROOTS, LUCENE}

	searchType currentSearchType = searchType.DEFAULT;

	// Version 1.7, Variables to be used for to choose between Derby and H2.
	public static final boolean derbyInUse = false;

	private JMenuItem importMenuItem;

	// Version 2.1
	WebCheckBoxTree<DefaultMutableTreeNode> searchTree, authorSearchTree;

	final CheckStateChangeListener<DefaultMutableTreeNode> authorSearchTreeChangeListener = new CheckStateChangeListener<>()
	{
		@Override
		public void checkStateChanged(WebCheckBoxTree<DefaultMutableTreeNode> authorSearchTree, List<CheckStateChange<DefaultMutableTreeNode>> checkStateChanges)
		{
			//for ( CheckStateChange change : checkStateChanges )
			//System.out.println ( change.getNode () + ": " + change.getOldState () + " -> " + change.getNewState () );

			//System.out.println(authorSearchTree.getCheckedNodes().size());
			//TreePath[] paths = e.getPaths();
			//for(TreePath path : paths)
			//System.out.println((e.isAddedPath(path) ? "Added - " : "Removed - ") + path);

			//searchTree.getCheckBoxTreeSelectionModel().removeSelectionPaths(new TreePath[]{new TreePath(searchTree.getModel().getRoot())}); // To deselect all the nodes. Not working [http://www.jidesoft.com/forum/viewtopic.php?f=18&t=12378&p=60863#p60863]
			//searchTree.getCheckBoxTreeSelectionModel().clearSelection();// OR removeSelectionPaths(new TreePath[]{searchTree.getPathForRow(0)}); // To deselect all the nodes
			searchTree.removeCheckStateChangeListener(searchTreeChangeListener);
			searchTree.getCheckingModel().uncheckAll(); // To deselect all the nodes

			final java.util.List<DefaultMutableTreeNode> checkedNodes = authorSearchTree.getNodes(CheckState.checked);
			if (!checkedNodes.isEmpty())
			{
				for (final DefaultMutableTreeNode node : checkedNodes)
				{
					//System.out.println("1: "+Arrays.toString(node.getPath()));
					if (node.isRoot())
					{
						//searchTree.setChecked(searchTree.getRootNode(), true); // To select all the nodes
						searchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously
						break;
					}

					final NodeInfo info = (NodeInfo) node.getUserObject();
					//if(path.getPathCount()==2) // i.e. author, there are many ways to do this using node.isLeaf/Root or info.category.equals("") ...
					if (info.category.equals("")) // Version 1.8
					{
						try
						{
							final Statement stmt = sharedDBConnection.createStatement();
							ResultSet rs = stmt.executeQuery("SELECT id FROM " + bookTableName + " WHERE author = '" + info.name + "' AND parent = ''"); // Version 2.1
							while (rs.next())
							{
								final int searchTreeId = rs.getInt("id");
								final Enumeration<TreeNode> searchTreeNodes = ((DefaultMutableTreeNode) searchTree.getModel().getRoot()).postorderEnumeration();
								while (searchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode searchTreeNode = (DefaultMutableTreeNode) searchTreeNodes.nextElement();
									if (searchTreeId == ((NodeInfo) (searchTreeNode.getUserObject())).id)
										searchTree.setChecked(searchTreeNode, true);
								}
							}

							rs = stmt.executeQuery("SELECT parent, category FROM " + bookTableName + " WHERE (author = '" + info.name + "' AND parent != '') GROUP BY parent, category");
							while (rs.next())
							{
								final String multiBookName = rs.getString("parent");
								final String category = rs.getString("category");
								final Enumeration<TreeNode> searchTreeNodes = ((DefaultMutableTreeNode) searchTree.getModel().getRoot()).postorderEnumeration();
								while (searchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode searchTreeNode = (DefaultMutableTreeNode) searchTreeNodes.nextElement();
									final NodeInfo item = (NodeInfo) searchTreeNode.getUserObject();
									if (item.author.equals(info.name) && item.category.equals(category) && item.name.equals(multiBookName))
									{
										searchTree.setChecked(searchTreeNode, true);
										break;
									}
								}
							}

							stmt.close();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
					}
					else
					{
						//if(path.getPathCount()==3) // i.e. leaf/book
						if (node.isLeaf())
						{
							if (info.path.isEmpty())
							{
								final Enumeration<TreeNode> searchTreeNodes = ((DefaultMutableTreeNode) searchTree.getModel().getRoot()).postorderEnumeration();
								while (searchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode searchTreeNode = (DefaultMutableTreeNode) searchTreeNodes.nextElement();
									final NodeInfo item = (NodeInfo) searchTreeNode.getUserObject();
									if (item.author.equals(info.author) && item.category.equals(info.category) && item.name.equals(info.name))
									{
										searchTree.setChecked(searchTreeNode, true);
										break;
									}
								}
							}
							else
							{
								final Enumeration<TreeNode> searchTreeNodes = ((DefaultMutableTreeNode) searchTree.getModel().getRoot()).postorderEnumeration();
								while (searchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode searchTreeNode = (DefaultMutableTreeNode) searchTreeNodes.nextElement();
									if (info.id == ((NodeInfo) (searchTreeNode.getUserObject())).id) // Version 2.1 id instead of path
									{
										searchTree.setChecked(searchTreeNode, true);
										break;
									}
								}
							}
						}
					}
				}
			}
			searchTree.addCheckStateChangeListener(searchTreeChangeListener);
		}
	};

	//searchTree.getCheckBoxTreeSelectionModel().setSingleEventMode(true);
	final CheckStateChangeListener<DefaultMutableTreeNode> searchTreeChangeListener = new CheckStateChangeListener<>()
	{
		@Override
		public void checkStateChanged(WebCheckBoxTree<DefaultMutableTreeNode> searchTree, List<CheckStateChange<DefaultMutableTreeNode>> checkStateChanges)
		{
			//for (CheckStateChange change : checkStateChanges)
			//	System.out.println(change.getNode() + ": " + change.getOldState() + " -> " + change.getNewState());

			//System.out.println(searchTree.getCheckedNodes().size());
			/*
			TreePath[] paths = e.getPaths();
			for(TreePath path : paths)
				System.out.println((e.isAddedPath(path) ? "Added - " : "Removed - ") + path);
			*/

			authorSearchTree.removeCheckStateChangeListener(authorSearchTreeChangeListener);
			authorSearchTree.getCheckingModel().uncheckAll(); // To deselect all the nodes

			final java.util.List<DefaultMutableTreeNode> checkedNodes = searchTree.getNodes(CheckState.checked);
			if (!checkedNodes.isEmpty())
			{
				for (final DefaultMutableTreeNode node : checkedNodes)
				{
					if (node.isRoot())
					{
						//authorSearchTree.getCheckBoxTreeSelectionModel().setSelectionPaths(new TreePath[]{new TreePath(authorSearchTree.getModel().getRoot())}); // To select all the nodes
						//authorSearchTree.setChecked(authorSearchTree.getRootNode(), true); // To select all the nodes
						authorSearchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously
						break;
					}

					final NodeInfo info = (NodeInfo) node.getUserObject();
					//if(path.getPathCount()==2) // i.e. category, there are many ways to do this using node.isLeaf/Root or info.category.equals("") ...
					if (info.category.equals("")) // Version 1.8
					{
						try
						{
							final Statement stmt = sharedDBConnection.createStatement();
							ResultSet rs = stmt.executeQuery("SELECT id FROM " + bookTableName + " WHERE category = '" + info.name + "' AND parent = ''"); // Version 2.1, replace path with id
							while (rs.next())
							{
								final int authorSearchTreeId = rs.getInt("id");
								final Enumeration<TreeNode> authorSearchTreeNodes = ((DefaultMutableTreeNode) authorSearchTree.getModel().getRoot()).postorderEnumeration();
								while (authorSearchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode authorSearchTreeNode = (DefaultMutableTreeNode) authorSearchTreeNodes.nextElement();
									if (authorSearchTreeId == ((NodeInfo) (authorSearchTreeNode.getUserObject())).id) // Version 2.1, id instead of path
										//authorSearchTree.getCheckBoxTreeSelectionModel().addSelectionPaths(new TreePath[]{new TreePath(authorSearchTreeNode.getPath())});
										authorSearchTree.setChecked(authorSearchTreeNode, true);
								}
							}

							rs = stmt.executeQuery("SELECT parent, author FROM " + bookTableName + " WHERE (category = '" + info.name + "' AND parent != '') GROUP BY parent, author");
							while (rs.next())
							{
								final String multiBookName = rs.getString("parent");
								final String author = rs.getString("author");
								final Enumeration<TreeNode> authorSearchTreeNodes = ((DefaultMutableTreeNode) authorSearchTree.getModel().getRoot()).postorderEnumeration();
								while (authorSearchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode authorSearchTreeNode = (DefaultMutableTreeNode) authorSearchTreeNodes.nextElement();
									final NodeInfo item = (NodeInfo) authorSearchTreeNode.getUserObject();
									if (item.author.equals(author) && item.category.equals(info.name) && item.name.equals(multiBookName))
									{
										//authorSearchTree.getCheckBoxTreeSelectionModel().addSelectionPaths(new TreePath[]{new TreePath(authorSearchTreeNode.getPath())});
										authorSearchTree.setChecked(authorSearchTreeNode, true);
										break;
									}
								}
							}

							stmt.close();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
					}
					else
					{
						//if(path.getPathCount()==3) // i.e. leaf/book
						if (node.isLeaf()) // No need for this
						{
							if (info.path.isEmpty())
							{
								final Enumeration<TreeNode> authorSearchTreeNodes = ((DefaultMutableTreeNode) authorSearchTree.getModel().getRoot()).postorderEnumeration();
								while (authorSearchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode authorSearchTreeNode = (DefaultMutableTreeNode) authorSearchTreeNodes.nextElement();
									final NodeInfo item = (NodeInfo) authorSearchTreeNode.getUserObject();
									if (item.author.equals(info.author) && item.category.equals(info.category) && item.name.equals(info.name))
									{
										//authorSearchTree.getCheckBoxTreeSelectionModel().addSelectionPaths(new TreePath[]{new TreePath(authorSearchTreeNode.getPath())});
										authorSearchTree.setChecked(authorSearchTreeNode, true);
										break;
									}
								}
							}
							else
							{
								final Enumeration<TreeNode> authorSearchTreeNodes = ((DefaultMutableTreeNode) authorSearchTree.getModel().getRoot()).postorderEnumeration();
								while (authorSearchTreeNodes.hasMoreElements())
								{
									final DefaultMutableTreeNode authorSearchTreeNode = (DefaultMutableTreeNode) authorSearchTreeNodes.nextElement();
									if (info.id == ((NodeInfo) (authorSearchTreeNode.getUserObject())).id) // Version 2.1, id instead of path
									{
										//authorSearchTree.getCheckBoxTreeSelectionModel().addSelectionPaths(new TreePath[]{new TreePath(authorSearchTreeNode.getPath())});
										authorSearchTree.setChecked(authorSearchTreeNode, true);
										break;
									}
								}
							}
						}
					}
				}
			}
			authorSearchTree.addCheckStateChangeListener(authorSearchTreeChangeListener);
		}
	};

	public ArabicIndexer()
	{
		String defaultLanguage;

		// Version 1.8
		try
		{
			// Determine the default language
			// Update version 2.1
			final Properties prop = new Properties();
			prop.load(new FileInputStream(ArabicIndexer.programFolder + "setting/setting.properties"));
			defaultLanguage = prop.getProperty("language");

			if (defaultLanguage.equals("nothing"))
				new DefaultLanguage(ArabicIndexer.this);
			else
				language = defaultLanguage.equals("Arabic") ? lang.Arabic : (defaultLanguage.equals("Urdu") ? lang.Urdu : lang.English);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		WebLookAndFeel.setLeftToRightOrientation(language == lang.English);

		final String[] translations = StreamConverter(programFolder + "language/ArabicIndexer" + language + ".txt");

		try
		{
			final String dbURL;

			// Version 1.7
			if (derbyInUse)
			{
				dbURL = "jdbc:derby:" + programFolder + "db"; // Version 1.9, to make it work in Mac where the call is from the jar but the folders are outside in the *.app
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
			}
			else
			{
				dbURL = "jdbc:h2:" + programFolder + "db/indexerDatabase"; //;MV_STORE=false;LOG=0;LOCK_MODE=0;UNDO_LOG=0;FILE_LOCK=NO;CACHE_SIZE=131072
				Class.forName("org.h2.Driver");
			}

			// Version 1.4, Global shared connection.
			sharedDBConnection = DriverManager.getConnection(dbURL);

			// To indicate the table name in the SQL statements.
			if (language == lang.English)
			{
				setTitle("Maknoon Manuscripts Indexer");
				bookTableName = "englishBook";
				defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "englishIndex").toPath())));

				// Version 1.5
				//IndexWriter.unlock(FSDirectory.open(new File("englishIndex")));
				indexWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "englishIndex").toPath()), new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(OpenMode.APPEND));
			}
			else
			{
				// It should be HARD CODED !!
				setTitle("برنامج مفهرس المخطوطات من موقع مكنون");
				bookTableName = "arabicBook";

				// Version 1.4, Opening global Searcher for performance gain.
				defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicIndex").toPath())));

				// Version 1.5
				//arabicRootsTableSearcher = new IndexSearcher(new RAMDirectory(FSDirectory.open(new File("rootsTableIndex")))); // This is faster but consumes memory, no need since 16MB is expensive
				arabicRootsSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicRootsIndex").toPath())));
				arabicLuceneSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicLuceneIndex").toPath())));

				arabicRootsAnalyzer = new ArabicRootAnalyzer();
				arabicLuceneAnalyzer = new org.apache.lucene.analysis.ar.ArabicAnalyzer();
				arabicAnalyzer = new ArabicAnalyzer();

				// Version 1.8, Deprecated, no replacement
				// To unlock the index if something happens
				//FSDirectory.open(new File("arabicIndex")).clearLock("write.lock"); // Different way
				//IndexWriter.unlock(FSDirectory.open(new File("arabicIndex")));
				//IndexWriter.unlock(FSDirectory.open(new File("arabicLuceneIndex")));
				//IndexWriter.unlock(FSDirectory.open(new File("arabicRootsIndex")));

				// Version 1.6, Reformat
				indexWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicIndex").toPath()), new IndexWriterConfig(arabicAnalyzer).setOpenMode(OpenMode.APPEND)); // ArabicGlossAnalyzer(), ArabicStemAnalyzer(), StandardAnalyzer
				arabicRootsWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicRootsIndex").toPath()), new IndexWriterConfig(arabicRootsAnalyzer).setOpenMode(OpenMode.APPEND));
				arabicLuceneWriter = new IndexWriter(FSDirectory.open(new File(programFolder + "arabicLuceneIndex").toPath()), new IndexWriterConfig(arabicLuceneAnalyzer).setOpenMode(OpenMode.APPEND));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			if (e.toString().contains("Locked by another process"))
			{
				getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
				JOptionPane.showOptionDialog(getContentPane(), translations[113], translations[114], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[115]}, translations[115]);
				System.exit(1);
			}
		}

		setBounds(0, 0, screenSize.width, screenSize.height - 40);

		if (language != lang.English)
		{
			UIManager.put("FileChooser.fileNameLabelText", translations[82]);
			UIManager.put("FileChooser.filesOfTypeLabelText", translations[83]);
			UIManager.put("FileChooser.cancelButtonText", translations[84]);
			UIManager.put("FileChooser.saveButtonText", translations[85]);
			UIManager.put("FileChooser.saveInLabelText", translations[86]);
			UIManager.put("FileChooser.lookInLabelText", translations[87]);
			UIManager.put("FileChooser.openButtonText", translations[88]);
			UIManager.put("FileChooser.viewMenuLabelText", translations[89]);
			UIManager.put("FileChooser.refreshActionLabelText", translations[90]);
			UIManager.put("FileChooser.newFolderActionLabelText", translations[91]);
			UIManager.put("FileChooser.listViewActionLabelText", translations[92]);
			UIManager.put("FileChooser.detailsViewActionLabelText", translations[93]);

			// Version 1.2
			UIManager.put("FileChooser.directoryOpenButtonText", translations[94]);
		}

		// Version 1.2, This is used to display images in the window list when switching to another application by pressing Alt-Tab.
		final ArrayList<Image> images = new ArrayList<>();
		images.add(Toolkit.getDefaultToolkit().createImage(programFolder + "images/icon.png"));
		images.add(Toolkit.getDefaultToolkit().createImage(programFolder + "images/icon_32.png"));
		images.add(Toolkit.getDefaultToolkit().createImage(programFolder + "images/icon_64.png"));
		images.add(Toolkit.getDefaultToolkit().createImage(programFolder + "images/icon_128.png")); // Version 1.6, Add it since it is there for Gnome3 new style.
		setIconImages(images);
		setJMenuBar(new JMenuBar() // Version 1.7
		{
			{
				add(new JMenu(translations[138]) // File
				{
					{
						importMenuItem = new JMenuItem(translations[139], new ImageIcon(programFolder + "images/import.png"));
						importMenuItem.addActionListener(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								new Update(ArabicIndexer.this, updateFiles);
							}
						});
						add(importMenuItem);

						add(new JMenuItem(translations[140], new ImageIcon(programFolder + "images/languages.png"))
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										new Setting(ArabicIndexer.this);
									}
								});
							}
						});

						// Version 1.8, Shamela Converter
						add(new JMenuItem(translations[116], new ImageIcon(programFolder + "images/convertor_icon.png"))
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										new ShamelaConverter(ArabicIndexer.this);
									}
								});
							}
						});

						add(new JMenuItem(translations[32], new ImageIcon(programFolder + "images/ocr.png"))
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										new Ocr(ArabicIndexer.this);
									}
								});
							}
						});

						addSeparator();

						add(new JMenuItem(translations[141])
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										shutdown();
									}
								});
							}
						});
					}
				});

				add(new JMenu(translations[142]) // Help
				{
					{
						add(new JMenuItem(translations[143], new ImageIcon(programFolder + "images/help.png"))
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										try
										{
											Desktop.getDesktop().browse(new URI("http://www.maknoon.com/arabicIndexer.php"));
										}
										catch (Exception ex)
										{
											ex.printStackTrace();
										}
									}
								});
							}
						});

						add(new JMenuItem(translations[144], new ImageIcon(programFolder + "images/about.png"))
						{
							{
								addActionListener(new ActionListener()
								{
									public void actionPerformed(ActionEvent e)
									{
										JOptionPane.showOptionDialog(getContentPane(),
												translations[145] + System.lineSeparator() +
														translations[146] + System.lineSeparator() +
														translations[147] + System.lineSeparator() +
														translations[148] + System.lineSeparator() +
														translations[149], translations[144], JOptionPane.YES_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{translations[44]}, translations[44]);
									}
								});
							}
						});
					}
				});

				if (language != lang.English)
					applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
			}
		});

		if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH))
			setExtendedState(Frame.MAXIMIZED_BOTH);

		// Create the nodes.
		treeRoot = new DefaultMutableTreeNode(new NodeInfo(translations[95], "", "treeRoot", "", "", "treeRoot", 0));
		authorTreeRoot = new DefaultMutableTreeNode(new NodeInfo(translations[13], "", "authorTreeRoot", "", "", "authorTreeRoot", 0));
		searchRoot = new DefaultMutableTreeNode(new NodeInfo(translations[95], "", "searchRoot", "", "", "searchRoot", 0));
		authorSearchRoot = new DefaultMutableTreeNode(new NodeInfo(translations[13], "", "authorSearchRoot", "", "", "authorSearchRoot", 0));

		final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
		final DefaultTreeModel authorTreeModel = new DefaultTreeModel(authorTreeRoot);

		// Version 1.8, WebCheckBoxTree instead of JIDE CheckBoxTree
		searchTree = new WebCheckBoxTree<>(new DefaultTreeModel(searchRoot)); // Version 2.0, add replace '(searchRoot)' with 'new DefaultTreeModel(searchRoot)'. It causes issues when deleting in WebLaF only (https://github.com/mgarin/weblaf/issues/633)
		authorSearchTree = new WebCheckBoxTree<>(new DefaultTreeModel(authorSearchRoot));

		tree = new JTree(treeModel);
		authorTree = new JTree(authorTreeModel);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		authorTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

		//searchTree.getCheckBoxTreeSelectionModel().setSingleEventMode(true);
		searchTree.addCheckStateChangeListener(searchTreeChangeListener);

		//authorSearchTree.getCheckBoxTreeSelectionModel().setSingleEventMode(true);
		authorSearchTree.addCheckStateChangeListener(authorSearchTreeChangeListener);

		// Enable tool tips
		ToolTipManager.sharedInstance().registerComponent(tree);
		ToolTipManager.sharedInstance().registerComponent(authorTree);
		final DefaultTreeCellRenderer treeCellRenderer = new DefaultTreeCellRenderer()
		{
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
				final NodeInfo nodeInfo = (NodeInfo) (node.getUserObject());
				if (leaf)
				{
					// Is Root Leaf ?
					if (node.isRoot())
						setIcon(new ImageIcon(programFolder + "images/rootLeaf.png"));
					else
					{
						if (nodeInfo.path.endsWith("pdf"))
							setIcon(new ImageIcon(programFolder + "images/PDF.png"));
						else
							setIcon(new ImageIcon(programFolder + "images/icon.png"));

						// Version 1.7
						setToolTipText("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "<font color=red>" + translations[117] + ":</font> " + nodeInfo.absolutePath + "<br><font color=red>" + translations[7] + ":</font> " + nodeInfo.author + "<br><font color=red>" + translations[8] + ":</font> " + nodeInfo.category);
					}
				}
				else
				{
					if (nodeInfo.path.equals("parent"))
					{
						// setClosedIcon, setOpenIcon will not work since they are used to all nodes.
						//if(expanded)
						setIcon(new ImageIcon(programFolder + "images/books.png"));
						//else
						//	setIcon(new ImageIcon("images/book_close.png"));
						if (authorTreeSelected)
							setToolTipText("<HTML><font color=red>" + translations[8] + ":</font> " + nodeInfo.category);
						else
							setToolTipText("<HTML><font color=red>" + translations[7] + ":</font> " + nodeInfo.author);
					}
					else
						setToolTipText(null); // no tool tip
				}
				return this;
			}
		};

		tree.setCellRenderer(treeCellRenderer);
		authorTree.setCellRenderer(treeCellRenderer);

		// enable anti-aliased text:
		//System.setProperty("awt.useSystemAAFontSettings","on"); // -Dawt.useSystemAAFontSettings=true

		final JTabbedPane treeTabbedPane = new JTabbedPane();
		treeTabbedPane.addTab(translations[57], new JScrollPane(tree));
		treeTabbedPane.addTab(translations[58], new JScrollPane(authorTree));
		treeTabbedPane.addChangeListener(new ChangeListener() // Version 1.7
		{
			public void stateChanged(ChangeEvent e)
			{
				enableEditPanel(false, translations[70]);
				tree.clearSelection();
				authorTree.clearSelection();
				authorTreeSelected = (treeTabbedPane.getSelectedIndex() == 1);
			}
		});

		final JPanel listPanel = new JPanel(new BorderLayout());
		listPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[96], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
		//listPanel.add(new JScrollPane(tree));
		listPanel.add(treeTabbedPane, BorderLayout.CENTER);

		// Version 1.3, This initialization shifted here to clear the results number when ordering
		final JPanel searchPanel = new JPanel(new BorderLayout());
		searchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[108], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));

		final JButton addButton = new JButton(new ImageIcon(programFolder + "images/add.png"));
		addButton.setToolTipText(translations[99]);
		final JMenuItem addMenuItem = new JMenuItem(translations[100], new ImageIcon(programFolder + "images/add.png"));
		final ActionListener AddActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (addType.isEmpty())
					// For initial case if nothing is selected.
					JOptionPane.showOptionDialog(getContentPane(), translations[4], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
				else
				{
					final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
					final TreePath[] treePaths = displayedTree.getSelectionPaths();

					if (treePaths.length == 1)
					{
						final JDialog addDialog = new JDialog(ArabicIndexer.this, translations[100], true);
						addDialog.setIconImage(Toolkit.getDefaultToolkit().createImage(programFolder + "images/add.png"));
						//addDialog.setResizable(false);

						final Vector<ResultsData> resultsDataVector = new Vector<>();
						final JTable table = new JTable();
						table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

						// Version 1.7, Paste cell [http://www.javaworld.com/javatips/jw-javatip77.html]
						final KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK, false);
						final Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
						table.registerKeyboardAction(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								if (e.getActionCommand().compareTo("Paste") == 0)
								{
									try
									{
										String trstring = (String) (system.getContents(this).getTransferData(DataFlavor.stringFlavor));
										table.setValueAt(trstring, table.getSelectedRow(), table.getSelectedColumn());
										table.updateUI();
									}
									catch (Exception ex)
									{
										ex.printStackTrace();
									}
								}
							}
						}, "Paste", paste, JComponent.WHEN_FOCUSED);

						final SimpleTableModel tableModel = new SimpleTableModel(resultsDataVector, translations[10], translations[19], translations[48], translations[7], translations[8], translations[20], addType.equals("parent"), (addType.equals("leaf") || addType.equals("parent")) && authorTreeSelected, (addType.equals("leaf") || addType.equals("parent")) && !authorTreeSelected); // Version 1.7
						table.setModel(tableModel);
						table.setPreferredScrollableViewportSize(new Dimension(800, 250));
						table.setColumnSelectionAllowed(true);

                        /* Version 1.6, No need for this since the user is able to delete/add
						table.addMouseListener(new MouseAdapter()
						{
							public void mouseClicked(MouseEvent e)
							{
								if(e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) == SimpleTableModel.PATH_COL)
								{
									final JFileChooser fc = new JFileChooser((String)tableModel.getValueAt(table.rowAtPoint(e.getPoint()), SimpleTableModel.PATH_COL));
                                    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

                                    // Version 1.5, Replace ExtensionFileFilter with FileNameExtensionFilter
                                    fc.setFileFilter(new FileNameExtensionFilter("PDF, Portable Document Format", "pdf")); // The first one with setFileFilter() instead of addChoosableFileFilter() because of the bug in linux that displays empty field for the first type in case using IcedTea Java !
                                    fc.setAcceptAllFileFilterUsed(false);
                                    fc.setDialogTitle(translations[12]);

									if(language)fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

									final int returnVal = fc.showOpenDialog(addDialog);
									if(returnVal == JFileChooser.APPROVE_OPTION)
									{
										// We need a check here with other paths.
										for(int i=0; i<tableModel.getRowCount(); i++)
										{
											if(String.valueOf(fc.getSelectedFile()).equals(tableModel.getValueAt(i, SimpleTableModel.PATH_COL)))
											{
												JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[46], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
												return;
											}
										}
										tableModel.setValueAt(String.valueOf(fc.getSelectedFile()), table.rowAtPoint(e.getPoint()), SimpleTableModel.PATH_COL);
									}
								}
							}

							/*
							public void mouseReleased(final MouseEvent e)
							{
								if(e.isPopupTrigger())
								{
									table.setRowSelectionInterval(table.rowAtPoint(e.getPoint()), table.rowAtPoint(e.getPoint()));

									final JPopupMenu tablePopup = new JPopupMenu();
									final JMenuItem menuItem = new JMenuItem(translations[47] + (table.rowAtPoint(e.getPoint())+1), new ImageIcon("images/delete.png"));
									menuItem.addActionListener(new ActionListener()
									{
										public void actionPerformed(ActionEvent ae)
										{
											tableModel.fireTableRowsDeleted(table.rowAtPoint(e.getPoint()), table.rowAtPoint(e.getPoint()));
										}
									});

									tablePopup.add(menuItem);
									if(language)tablePopup.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

					        		final Dimension dim = new Dimension();
					        		tablePopup.show(table, e.getX(), e.getY());
					        		if(language)
					        		{
						        		tablePopup.getSize(dim);
						        		tablePopup.show(table, e.getX()-dim.width+2, e.getY());
						        	}
								}
							}
							*
						});
						*/

						final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
						final DefaultTableCellRenderer disabledRenderer = new DefaultTableCellRenderer();

						if (language != lang.English)
						{
							renderer.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
							disabledRenderer.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
						}
						disabledRenderer.setEnabled(false);

						for (int i = 0; i < tableModel.getColumnCount(); i++)
						{
							final TableColumn column = table.getColumnModel().getColumn(i);
							if (i == SimpleTableModel.PATH_COL)
								column.setPreferredWidth(300);
							else
							{
								column.setPreferredWidth(120);

								if (addType.equals("parent") && i == SimpleTableModel.PARENT_NAME_COL)
									column.setCellRenderer(disabledRenderer);
								else
								{
									if ((addType.equals("leaf") || addType.equals("parent")) && i == SimpleTableModel.CATEGORY_COL && !authorTreeSelected)
										column.setCellRenderer(disabledRenderer);
									else
									{
										if ((addType.equals("leaf") || addType.equals("parent")) && i == SimpleTableModel.AUTHOR_COL && authorTreeSelected) // Version 1.7
											column.setCellRenderer(disabledRenderer);
										else
										{
											if (i == SimpleTableModel.TYPE_COL)
												column.setPreferredWidth(35); // It should NOT be rendered to display the icon
											else
												column.setCellRenderer(renderer);
										}
									}
								}
							}
						}

						// Creating the JTable by passing the table model
						addDialog.add(new JScrollPane(table), BorderLayout.CENTER);

						final JButton browseButton = new JButton(translations[100], new ImageIcon(programFolder + "images/add.png"));
						browseButton.setToolTipText(translations[12]);
						browseButton.addActionListener(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								final JFileChooser fc = new JFileChooser();
								fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

								// Version 1.5, Replace ExtensionFileFilter with FileNameExtensionFilter
								fc.setFileFilter(new FileNameExtensionFilter("PDF, Portable Document Format", "pdf")); // The first one with setFileFilter() instead of addChoosableFileFilter() because of the bug in linux that displays empty field for the first type.
								fc.setAcceptAllFileFilterUsed(false);
								if (language != lang.English)
									fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
								fc.setDialogTitle(translations[12]);
								fc.setMultiSelectionEnabled(true);

								final int returnVal = fc.showOpenDialog(addDialog);
								if (returnVal == JFileChooser.APPROVE_OPTION)
								{
									final File[] addedBookPath = fc.getSelectedFiles();

									// Version 1.6
									for (File element : addedBookPath)
									{
										boolean add = true;

										// We need a check here with other paths.
										for (int i = 0; i < tableModel.getRowCount(); i++)
										{
											if (element.toString().equals(tableModel.getValueAt(i, SimpleTableModel.PATH_COL)))
											{
												add = false;
												break;
											}
										}

										if (add)
											resultsDataVector.addElement(new ResultsData(new ImageIcon(programFolder + (new File(element.toString()).isFile() ? "images/books.png" : "images/icon.png")), element.toString(), "", addType.equals("parent") ? bookName : "", ((addType.equals("leaf") || addType.equals("parent")) && authorTreeSelected) ? bookAuthor : "", ((addType.equals("leaf") || addType.equals("parent")) && !authorTreeSelected) ? bookCategory : "")); // Version 1.7
									}
									table.updateUI();
								}
							}
						});

						// Version 1.6
						final JButton removeButton = new JButton(translations[102], new ImageIcon(programFolder + "images/cancel.png"));
						removeButton.addActionListener(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								int numRow = table.getSelectedRow();
								if (numRow != -1)
									tableModel.removeRow(numRow);
							}
						});

						final JButton OKButton = new JButton(translations[11], new ImageIcon(programFolder + "images/ok.png"));
						OKButton.addActionListener(new ActionListener()
						{
							public void actionPerformed(ActionEvent e)
							{
								if (tableModel.getRowCount() == 0)
									JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[14], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
								else
								{
									final Vector<String> bookNames = new Vector<>();
									final Vector<String> bookParents = new Vector<>();
									final Vector<String> bookPaths = new Vector<>();
									final Vector<String> bookAuthors = new Vector<>();
									final Vector<String> bookCategories = new Vector<>();
									boolean cont = true;
									for (int i = 0; i < tableModel.getRowCount(); i++)
									{
										// Version 1.9
										final File f = new File((String) tableModel.getValueAt(i, SimpleTableModel.PATH_COL));
										if (f.getParent().equals(new File(programFolder + "pdf").toString()))
											bookPaths.addElement("root:pdf" + File.separator + f.getName());
										else
											bookPaths.addElement(f.toString());

										bookParents.addElement((String) tableModel.getValueAt(i, SimpleTableModel.PARENT_NAME_COL));

										final String fieldName = (String) tableModel.getValueAt(i, SimpleTableModel.NAME_COL);
										final String fieldAuthor = (String) tableModel.getValueAt(i, SimpleTableModel.AUTHOR_COL);
										final String fieldCategory = (String) tableModel.getValueAt(i, SimpleTableModel.CATEGORY_COL);
										if (fieldName.isEmpty() || fieldAuthor.isEmpty() || fieldCategory.isEmpty())
										{
											cont = false;
											JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[22], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
											break;
										}
										bookNames.addElement(fieldName);
										bookAuthors.addElement(fieldAuthor);
										bookCategories.addElement(fieldCategory);
									}

									if (cont)
									{
										try
										{
											final Statement stmt = sharedDBConnection.createStatement();
											for (int i = 0; i < bookNames.size(); i++)
											{
												final int pageCount = getDocumentPageCount(bookPaths.elementAt(i).replaceFirst("root:pdf", eProgramFolder + "pdf")); // Version 1.9
												if (pageCount != 0)
												{
													try
													{
														int id = 0;
														final PreparedStatement ps1 = sharedDBConnection.prepareStatement("INSERT INTO " + bookTableName + " VALUES (default, '" + bookNames.elementAt(i) + "', '" + bookParents.elementAt(i) + "', '" + bookCategories.elementAt(i) + "', '" + bookAuthors.elementAt(i) + "', '" + bookPaths.elementAt(i) + "')", Statement.RETURN_GENERATED_KEYS);
														ps1.executeUpdate();
														try (ResultSet rs = ps1.getGeneratedKeys();)
														{
															if (rs.next())
																id = rs.getInt(1);
														}

														// Creating the indexing DB for the document.
														// Version 2.1, id instead of bookPaths.elementAt(i)
														stmt.execute("CREATE TABLE b" + id + "(page INTEGER, content CLOB(400000))"); // Version 1.8, CLOB instead of VARCHAR or LONGVARCHAR (H2) or LONG VARCHAR (Derby).
														stmt.execute("CREATE UNIQUE INDEX i" + id + "Page ON b" + id + "(page)");
														stmt.execute("INSERT INTO b" + id + " VALUES (" + pageCount + ", '')"); // To create the last page at least or you cannot display/edit it later
													}
													catch (Exception ex)
													{
														final String error = ex.getMessage();
														if (error.contains("already exists")) // Version 1.6
														{
															final int first = error.indexOf('"');
															final int second = error.indexOf('"', first + 1);
															JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[18] + System.lineSeparator() + error.substring(first + 1, second), translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
														}
													}
												}
												else
												{
													if (new File(bookPaths.elementAt(i)).isDirectory()) // i.e. Manuscript folder
														JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[49] + System.lineSeparator() + bookNames.elementAt(i), translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
													else
														JOptionPane.showOptionDialog(addDialog.getContentPane(), translations[71] + System.lineSeparator() + bookNames.elementAt(i), translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
												}

												// Version 1.3
												//if(addManuscripts)
												//	stmt.execute("INSERT INTO "+bookTableName+" VALUES ('"+bookNames.elementAt(i)+"', '"+bookParents.elementAt(i)+"', '"+((addType.equals("leaf"))?bookCategory:categoryTextField.getText().trim())+"', 'true', '"+bookPaths.elementAt(i)+"')");
												//else
												// Version 1.6, moved above
												//stmt.execute("INSERT INTO "+bookTableName+" VALUES ('"+bookNames.elementAt(i)+"', '"+bookParents.elementAt(i)+"', '"+bookCategories.elementAt(i)+"', '"+bookAuthors.elementAt(i)+"', '"+bookPaths.elementAt(i)+"')");
											}
											stmt.close();

											// To refresh detailList
											createNodes();

											addDialog.dispose();
										}
										catch (Exception ex)
										{
											ex.printStackTrace();
										}
									}
								}
							}
						});

						final JPanel controlPanel_east = new JPanel();
						controlPanel_east.add(removeButton);
						controlPanel_east.add(browseButton);

						final JPanel controlPanel_west = new JPanel();
						controlPanel_west.add(OKButton);

						final JPanel controlPanel = new JPanel(new BorderLayout());
						if (language == lang.English)
						{
							controlPanel.add(controlPanel_west, BorderLayout.EAST);
							controlPanel.add(controlPanel_east, BorderLayout.WEST);
						}
						else
						{
							controlPanel.add(controlPanel_west, BorderLayout.WEST);
							controlPanel.add(controlPanel_east, BorderLayout.EAST);
						}

						addDialog.add(controlPanel, BorderLayout.SOUTH);
						if (language != lang.English)
							addDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

						addDialog.pack();
						centerInScreen(addDialog);
						addDialog.setVisible(true);

						// Version 1.7, capability to drag files to Add Window. Works in Windows/Mac/Linux.
						// TODO: Not working when dialog modal = true
						/*
						addDialog.setTransferHandler(new TransferHandler()
						{
							public boolean canImport(TransferSupport support)
							{
								if(support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) // Supported by Windows/Mac
								{
									try
									{
										final java.util.List fileList = (java.util.List)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
										for (Object file : fileList)
											if(file instanceof File)
											{
												final File f = (File) file;
												if(!(f.getName().endsWith(".pdf") || f.getName().endsWith(".PDF")))
													return false;
											}
									}
									catch(Exception e){e.printStackTrace();}
									return true;
								}
								else
								{
									if(support.isDataFlavorSupported(DataFlavor.stringFlavor)) // Supported by Linux
									{
										try
										{
											final String paths[] = ((String)support.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim().split("\\r?\\n");
											for(String p : paths)
												if(!(p.endsWith(".pdf") || p.endsWith(".PDF")))
													return false;
										}
										catch(Exception e){e.printStackTrace();}
										return true;
									}
								}
								return false;
							}

							public boolean importData(TransferSupport support)
							{
								try
								{
									if(support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) // Supported by Windows/Mac
									{
										final java.util.List fileList = (java.util.List)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
										for (Object file : fileList)
											if (file instanceof File)
											{
												boolean add = true;
												final String addedBookPath = ((File) file).getAbsolutePath();

												// We need a check here with other paths.
												for(int i=0; i<tableModel.getRowCount(); i++)
												{
													if(addedBookPath.equals(tableModel.getValueAt(i, SimpleTableModel.PATH_COL)))
													{
														add = false;
														break;
													}
												}

												if(add)
													resultsDataVector.addElement(new ResultsData(new ImageIcon(new File(addedBookPath).isFile()?"images/books.png":"images/icon.png"), addedBookPath, "", addType.equals("parent")?bookName:"", ((addType.equals("leaf") || addType.equals("parent")) && authorTreeSelected)?bookAuthor:"", ((addType.equals("leaf") || addType.equals("parent")) && !authorTreeSelected)?bookCategory:"")); // Version 1.7
												table.updateUI();
											}

										//final Thread thread = new Thread(){public void run(){importMenuItem.doClick();}};
										//thread.start();
										return true;
									}
									else // Drop from GNOME
									{
										if(support.isDataFlavorSupported(DataFlavor.stringFlavor)) // Supported by Linux
										{
											final String paths[] = ((String)support.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim().split("\\r?\\n");
											for(String p : paths)
												if(p.startsWith("file://")) // No Need for this
												{
													boolean add = true;
													final String addedBookPath = new URI(p).getPath();

													// We need a check here with other paths.
													for(int i=0; i<tableModel.getRowCount(); i++)
													{
														if(addedBookPath.equals(tableModel.getValueAt(i, SimpleTableModel.PATH_COL)))
														{
															add = false;
															break;
														}
													}

													if(add)
														resultsDataVector.addElement(new ResultsData(new ImageIcon(new File(addedBookPath).isFile()?"images/books.png":"images/icon.png"), addedBookPath, "", addType.equals("parent")?bookName:"", ((addType.equals("leaf") || addType.equals("parent")) && authorTreeSelected)?bookAuthor:"", ((addType.equals("leaf") || addType.equals("parent")) && !authorTreeSelected)?bookCategory:"")); // Version 1.7
													table.updateUI();
												}

											//final Thread thread = new Thread(){public void run(){importMenuItem.doClick();}};
											//thread.start();
											return true;
										}
									}
								}
								catch(Exception e){e.printStackTrace();}
								return false;
							}
						});
						*/
					}
					else
						JOptionPane.showOptionDialog(getContentPane(), translations[36], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
				}
			}

			/*
			public void disableContainer(Container cont)
			{
				Component[] components = cont.getComponents();
				for(int i=0; i<components.length; i++)
				{
					System.out.println(components[i]);
					//if((components[i] instanceof JCheckBox))
						components[i].setEnabled(false);

					if(components[i] instanceof Container)
						disableContainer((Container)components[i]);
				}
			}
			*/
		};
		addButton.addActionListener(AddActionListener);
		addMenuItem.addActionListener(AddActionListener);

		final JButton editButton = new JButton(new ImageIcon(programFolder + "images/edit.png"));
		editButton.setToolTipText(translations[101]);
		final JMenuItem editMenuItem = new JMenuItem(translations[101], new ImageIcon(programFolder + "images/edit.png"));
		final ActionListener EditActionListener = new ActionListener()
		{
			String selectedBookCategory = null;

			public void actionPerformed(ActionEvent e)
			{
				final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
				final TreePath[] treePaths = displayedTree.getSelectionPaths();
				if (treePaths != null)
				{
					if (treePaths.length == 1)
					{
						if (leafNode)
						{
							if (rootNode)
								JOptionPane.showOptionDialog(getContentPane(), translations[9], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
							else
							{
								if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), translations[52], translations[3], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[44], translations[6]}, translations[44]))
								{
									// Editing a book whether it is a one volume of a multi-volume book or it is a single book.
									final JDialog editDialog = new JDialog(ArabicIndexer.this, translations[101], true);
									editDialog.setResizable(false);

									final JTextField bookTextField = new JTextField(bookName, 35);
									final JTextField bookAuthorTextField = new JTextField(bookAuthor, 35);

									//final JTextField bookPathTextField = new JTextField(30); // Version 1.6, Deleted since it will just create a new book.
									final Vector<String> categories = new Vector<>();

									try
									{
										final Statement stmt = sharedDBConnection.createStatement();
										final ResultSet rs = stmt.executeQuery("SELECT category FROM " + bookTableName + " GROUP BY category");

										while (rs.next())
											categories.addElement(rs.getString("category"));

										stmt.close();
									}
									catch (Exception ex)
									{
										ex.printStackTrace();
									}

									final JComboBox<String> categoryComboBox = new JComboBox<>(categories);
									if (!bookParentName.isEmpty()) categoryComboBox.setEnabled(false);
									categoryComboBox.setSelectedIndex(categories.indexOf(bookCategory));
									selectedBookCategory = bookCategory;
									categoryComboBox.addActionListener(new ActionListener()
									{
										public void actionPerformed(ActionEvent e)
										{
											selectedBookCategory = categories.elementAt(((JComboBox) e.getSource()).getSelectedIndex());
										}
									});

									final JButton OKButton = new JButton(translations[11], new ImageIcon(programFolder + "images/ok.png"));
									OKButton.addActionListener(new ActionListener()
									{
										public void actionPerformed(ActionEvent e)
										{
											if (bookTextField.getText().trim().isEmpty() || bookAuthorTextField.getText().trim().isEmpty())
												JOptionPane.showOptionDialog(editDialog.getContentPane(), translations[33], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
											else
											{
												try
												{
													final Statement stmt = sharedDBConnection.createStatement();
													stmt.executeUpdate("UPDATE " + bookTableName + " SET name = '" + bookTextField.getText().trim() + "', category = '" + selectedBookCategory + "', author = '" + bookAuthorTextField.getText().trim() + "' WHERE id = " + bookId); // version 2.1
													stmt.close();

													// Delete the book index
													indexWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
													indexWriter.commit();

													if (language != lang.English)
													{
														arabicRootsWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
														arabicLuceneWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
														arabicRootsWriter.commit();
														arabicLuceneWriter.commit();
													}

													// To refresh detailList
													reopenIndexSearcher();

													createNodes();
													editDialog.dispose();
												}
												catch (Exception ex)
												{
													ex.printStackTrace();
												}
											}
										}
									});

									final JPanel decoratePanel = new JPanel();
									decoratePanel.add(OKButton);
									editDialog.add(decoratePanel, BorderLayout.SOUTH);

									final JPanel editPanel = new JPanel(new GridLayout(3, 1));
									editPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[50], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
									editPanel.add(bookTextField);
									editPanel.add(bookAuthorTextField);
									editPanel.add(categoryComboBox);
									editDialog.add(editPanel, BorderLayout.NORTH);
									if (language != lang.English)
										editDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

									editDialog.pack();
									centerInScreen(editDialog);
									editDialog.setVisible(true);
								}
							}
						}
						else
						{
							if (rootNode)
								JOptionPane.showOptionDialog(getContentPane(), translations[9], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
							else
							{
								if (addType.equals("parent")) // i.e. you will edit multi-volume book
								{
									if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), translations[54], translations[3], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[44], translations[6]}, translations[44]))
									{
										// Editing a multi-volumn book
										final JDialog editDialog = new JDialog(ArabicIndexer.this, translations[101], true);
										editDialog.setResizable(false);

										final JTextField bookTextField = new JTextField(bookName, 35);
										final JTextField bookAuthorTextField = new JTextField(bookAuthor, 35);
										final Vector<String> categories = new Vector<>();

										try
										{
											final Statement stmt = sharedDBConnection.createStatement();
											final ResultSet rs = stmt.executeQuery("SELECT category FROM " + bookTableName + " GROUP BY category");

											while (rs.next())
												categories.addElement(rs.getString("category"));

											stmt.close();
										}
										catch (Exception ex)
										{
											ex.printStackTrace();
										}

										final JComboBox<String> categoryComboBox = new JComboBox<>(categories);
										categoryComboBox.setSelectedIndex(categories.indexOf(bookCategory));
										selectedBookCategory = bookCategory;
										categoryComboBox.addActionListener(new ActionListener()
										{
											public void actionPerformed(ActionEvent e)
											{
												selectedBookCategory = categories.elementAt(((JComboBox) e.getSource()).getSelectedIndex());
											}
										});

										final JButton OKButton = new JButton(translations[11], new ImageIcon(programFolder + "images/ok.png"));
										OKButton.addActionListener(new ActionListener()
										{
											public void actionPerformed(ActionEvent e)
											{
												if (bookTextField.getText().trim().isEmpty() || bookAuthorTextField.getText().trim().isEmpty())
													JOptionPane.showOptionDialog(editDialog.getContentPane(), translations[33], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
												else
												{
													try
													{
														final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
														final ResultSet rs = stmt.executeQuery("SELECT * FROM " + bookTableName + " WHERE category = '" + bookCategory + "' AND parent = '" + bookName + "' AND author = '" + bookAuthor + "'"); // Version 1.7, ... AND author =  ...
														while (rs.next())
														{
															//final String bookPath = rs.getString("path"); // Version 2.1, removed
															final int bookId = rs.getInt("id");
															rs.updateString("parent", bookTextField.getText().trim());
															rs.updateString("category", selectedBookCategory);
															rs.updateString("author", bookAuthorTextField.getText().trim());
															rs.updateRow();

															//indexWriter.deleteDocuments(new Term("path", bookPath)); // Version 2.1, removed
															indexWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));

															if (language != lang.English)
															{
																//arabicRootsWriter.deleteDocuments(new Term("path", bookPath)); // Version 2.1
																//arabicLuceneWriter.deleteDocuments(new Term("path", bookPath));
																arabicRootsWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
																arabicLuceneWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
															}
														}
														rs.close();
														indexWriter.commit();

														if (language != lang.English)
														{
															arabicRootsWriter.commit();
															arabicLuceneWriter.commit();
														}

														//stmt.executeUpdate("UPDATE "+bookTableName+" SET parent = '"+bookTextField.getText().trim()+"', category = '"+selectedBookCategory+"' WHERE parent = '"+bookName+"' AND category = '"+bookCategory+"'");
														stmt.close();

														// To refresh detailList
														createNodes();
														reopenIndexSearcher();
														editDialog.dispose();
													}
													catch (Exception ex)
													{
														ex.printStackTrace();
													}
												}
											}
										});

										final JPanel decoratePanel = new JPanel();
										decoratePanel.add(OKButton);

										final JPanel editPanel = new JPanel(new GridLayout(3, 1));
										editPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[55], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
										editPanel.add(bookTextField);
										editPanel.add(bookAuthorTextField);
										editPanel.add(categoryComboBox);
										editDialog.add(editPanel, BorderLayout.CENTER);
										editDialog.add(decoratePanel, BorderLayout.SOUTH);
										if (language != lang.English)
											editDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

										editDialog.pack();
										centerInScreen(editDialog);
										editDialog.setVisible(true);
									}
								}
								else
								{
									if (authorTreeSelected)
									{
										if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), translations[40], translations[3], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[44], translations[6]}, translations[44]))
										{
											// Editing author
											final JDialog editDialog = new JDialog(ArabicIndexer.this, translations[101], true);
											editDialog.setResizable(false);

											final JTextField authorTextField = new JTextField(bookAuthor, 35);
											final JButton OKButton = new JButton(translations[11], new ImageIcon(programFolder + "images/ok.png"));
											OKButton.addActionListener(new ActionListener()
											{
												public void actionPerformed(ActionEvent e)
												{
													if (authorTextField.getText().trim().isEmpty())
														JOptionPane.showOptionDialog(editDialog.getContentPane(), translations[43], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
													else
													{
														try
														{
															final Statement stmt = sharedDBConnection.createStatement();
															stmt.executeUpdate("UPDATE " + bookTableName + " SET author = '" + authorTextField.getText().trim() + "' WHERE author = '" + bookAuthor + "'");

															indexWriter.deleteDocuments(new Term("author", bookAuthor));
															indexWriter.commit();

															if (language != lang.English)
															{
																arabicRootsWriter.deleteDocuments(new Term("author", bookAuthor));
																arabicLuceneWriter.deleteDocuments(new Term("author", bookAuthor));
																arabicRootsWriter.commit();
																arabicLuceneWriter.commit();
															}
															stmt.close();

															// To refresh detailList
															createNodes();
															reopenIndexSearcher();
															editDialog.dispose();
														}
														catch (Exception ex)
														{
															ex.printStackTrace();
														}
													}
												}
											});

											final JPanel decoratePanel = new JPanel();
											decoratePanel.add(OKButton);

											final JPanel editPanel = new JPanel(new BorderLayout());
											editPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[42], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
											editPanel.add(authorTextField, BorderLayout.CENTER);
											editDialog.add(editPanel, BorderLayout.CENTER);
											editDialog.add(decoratePanel, BorderLayout.SOUTH);
											if (language != lang.English)
												editDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

											editDialog.pack();
											centerInScreen(editDialog);
											editDialog.setVisible(true);
										}
									}
									else
									{
										if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), translations[53], translations[3], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[44], translations[6]}, translations[44]))
										{
											// Editing a category
											final JDialog editDialog = new JDialog(ArabicIndexer.this, translations[101], true);
											editDialog.setResizable(false);

											final JTextField categoryTextField = new JTextField(bookCategory, 35);
											final JButton OKButton = new JButton(translations[11], new ImageIcon(programFolder + "images/ok.png"));
											OKButton.addActionListener(new ActionListener()
											{
												public void actionPerformed(ActionEvent e)
												{
													if (categoryTextField.getText().trim().isEmpty())
														JOptionPane.showOptionDialog(editDialog.getContentPane(), translations[31], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
													else
													{
														try
														{
															final Statement stmt = sharedDBConnection.createStatement();
															stmt.executeUpdate("UPDATE " + bookTableName + " SET category = '" + categoryTextField.getText().trim() + "' WHERE category = '" + bookCategory + "'");

															indexWriter.deleteDocuments(new Term("category", bookCategory));
															indexWriter.commit();

															if (language != lang.English)
															{
																arabicRootsWriter.deleteDocuments(new Term("category", bookCategory));
																arabicLuceneWriter.deleteDocuments(new Term("category", bookCategory));
																arabicRootsWriter.commit();
																arabicLuceneWriter.commit();
															}
															stmt.close();

															// To refresh detailList
															createNodes();
															reopenIndexSearcher();
															editDialog.dispose();
														}
														catch (Exception ex)
														{
															ex.printStackTrace();
														}
													}
												}
											});

											final JPanel decoratePanel = new JPanel();
											decoratePanel.add(OKButton);

											final JPanel editPanel = new JPanel(new BorderLayout());
											editPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[15], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
											editPanel.add(categoryTextField, BorderLayout.CENTER);
											editDialog.add(editPanel, BorderLayout.CENTER);
											editDialog.add(decoratePanel, BorderLayout.SOUTH);
											if (language != lang.English)
												editDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

											editDialog.pack();
											centerInScreen(editDialog);
											editDialog.setVisible(true);
										}
									}
								}
							}
						}
					}
					else
						JOptionPane.showOptionDialog(getContentPane(), translations[35], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
				}
				else
					JOptionPane.showOptionDialog(getContentPane(), translations[34], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
			}
		};
		editButton.addActionListener(EditActionListener);
		editMenuItem.addActionListener(EditActionListener);

		final JButton deleteButton = new JButton(new ImageIcon(programFolder + "images/cancel.png"));
		deleteButton.setToolTipText(translations[102]);
		final JMenuItem deleteMenuItem = new JMenuItem(translations[102], new ImageIcon(programFolder + "images/cancel.png"));
		final ActionListener DeleteActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
				final TreePath[] treePaths = displayedTree.getSelectionPaths();
				if (treePaths != null)
				{
					DefaultMutableTreeNode currentNode;
					String deletedItemsLabel = "";
					for (TreePath element : treePaths)
					{
						currentNode = (DefaultMutableTreeNode) (element.getLastPathComponent());

						// Trying to delete Parent tree node i.e. base reference
						//if(String.valueOf(currentNode).equals(translations[23])) // Version 1.5, Removed
						if (currentNode.isRoot())
						{
							deletedItemsLabel = "";
							JOptionPane.showOptionDialog(getContentPane(), translations[24], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
							break;
						}
						else
							deletedItemsLabel = deletedItemsLabel + System.lineSeparator() + element;
					}

					if (!deletedItemsLabel.isEmpty()) // Version 1.7
					{
						// Version 1.5, Enable scrolling in JOptionPane when it exceeds screen limit using getOptionPaneScrollablePanel().
						final int choice = JOptionPane.showOptionDialog(getContentPane(), getOptionPaneScrollablePanel(translations[25], deletedItemsLabel.substring(System.lineSeparator().length())), translations[102], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[0], translations[1]}, translations[1]);
						//final int choice = JOptionPane.showOptionDialog(getContentPane(), translations[25] + deletedItemsLabel + lineSeparator + translations[71], translations[102], JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[0], translations[1], translations[6]}, translations[6]);
						if (choice == JOptionPane.YES_OPTION) //  No need: '&& JOptionPane.CLOSED_OPTION != choice'
						{
							final JDialog deleteProgressDialog = new JDialog(ArabicIndexer.this, translations[51], true);
							deleteProgressDialog.setLayout(new FlowLayout());
							deleteProgressDialog.setResizable(false);

							// To not enable the user to use the DB since its locked by the thread.
							deleteProgressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

							final JProgressBar deleteProgressBar = new JProgressBar();
							deleteProgressBar.setString(translations[51]);
							deleteProgressBar.setStringPainted(true);
							deleteProgressBar.setIndeterminate(true);
							deleteProgressDialog.getContentPane().add(deleteProgressBar);
							if (language != lang.English)
								deleteProgressDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

							deleteProgressDialog.pack();
							centerInScreen(deleteProgressDialog);

							final Thread thread = new Thread()
							{
								public void run()
								{
									try
									{
										final Statement stmt1 = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
										final Statement stmt2 = sharedDBConnection.createStatement();

										//System.out.println(indexModifier.docCount() + " docs before delete in index");
										for (TreePath element : treePaths)
										{
											SwingUtilities.invokeAndWait(new Runnable()
											{
												public void run()
												{
													displayedTree.setSelectionPath(element);
												}
											});

											if (leafNode)
											{
												stmt1.executeUpdate("DELETE FROM " + bookTableName + " WHERE id = " + bookId);
												stmt2.execute("DROP TABLE b" + bookId); // Version 1.6
												indexWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));// by default it will consider it as one world because term is one world.

												if (language != lang.English)
												{
													arabicRootsWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
													arabicLuceneWriter.deleteDocuments(new Term("id", String.valueOf(bookId)));
												}
											}
											else
											{
												final ResultSet rs;
												if (addType.equals("parent"))
												{
													//if(authorTreeSelected)
													rs = stmt1.executeQuery("SELECT id FROM " + bookTableName + " WHERE author = '" + bookAuthor + "' AND parent = '" + bookName + "' AND category = '" + bookCategory + "'"); // Version 1.7, To allow delete multi-volume books with the same name
													//else
													//   rs = stmt1.executeQuery("SELECT id FROM "+bookTableName+" WHERE category = '" + bookCategory + "' AND parent = '"+bookName+"'");
													//stmt.executeUpdate("DELETE FROM "+bookTableName+" WHERE category = '" + bookCategory + "' AND name = '"+bookName+"'");
												}
												else
												{
													if (authorTreeSelected)
														rs = stmt1.executeQuery("SELECT id FROM " + bookTableName + " WHERE author = '" + bookAuthor + "'");
													else
														rs = stmt1.executeQuery("SELECT id FROM " + bookTableName + " WHERE category = '" + bookCategory + "'");
													//stmt.executeUpdate("DELETE FROM "+bookTableName+" WHERE category = '" + bookCategory + "'");
												}

												while (rs.next())
												{
													//final String path = rs.getString("path");// Version 2.1
													final int id = rs.getInt("id");

													indexWriter.deleteDocuments(new Term("id", String.valueOf(id)));
													//stmt2.execute("DROP TABLE \"" + path + "\""); // Version 1.6
													stmt2.execute("DROP TABLE b" + id);

													if (language != lang.English)
													{
														arabicRootsWriter.deleteDocuments(new Term("id", String.valueOf(id)));
														arabicLuceneWriter.deleteDocuments(new Term("id", String.valueOf(id)));
													}
													rs.deleteRow();
												}
												rs.close();
											}

											//System.out.println("Deleted: "+indexModifier.deleteDocuments(new Term("path", bookPath)));// by default it will consider it as one world because term is one world.
											//indexModifier.deleteDocuments(new Term("path", repeatedIndexedBookPathVector.elementAt(i)));

											bookName = "";
											bookPath = "";
											bookId = 0;
											bookAbsolutePath = "";
											bookCategory = "";
											bookAuthor = "";
											addType = "";
											bookParentName = "";
										}

										indexWriter.commit();
										if (language != lang.English)
										{
											arabicRootsWriter.commit();
											arabicLuceneWriter.commit();
										}

										stmt1.close();
										stmt2.close();

										// To refresh detailList
										reopenIndexSearcher();

										SwingUtilities.invokeAndWait(new Runnable()
										{
											public void run()
											{
												createNodes();
												deleteProgressDialog.dispose();
											}
										});
									}
									catch (Exception e)
									{
										e.printStackTrace();
									}
								}
							};
							thread.start();
							deleteProgressDialog.setVisible(true);
						}
					}
				}
				else
					JOptionPane.showOptionDialog(getContentPane(), translations[26], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
			}
		};
		deleteButton.addActionListener(DeleteActionListener);
		deleteMenuItem.addActionListener(DeleteActionListener);

		final KeyListener keyListener = new KeyListener()
		{
			public void keyTyped(KeyEvent e)
			{
			}

			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_F5) orderButton.doClick();
				if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteMenuItem.doClick(); // Version 1.5
				if (e.getKeyCode() == KeyEvent.VK_F2) editMenuItem.doClick(); // Version 1.5
			}

			public void keyReleased(KeyEvent e)
			{
			}
		};
		tree.addKeyListener(keyListener);
		authorTree.addKeyListener(keyListener);

		final JButton indexingButton = new JButton(new ImageIcon(programFolder + "images/indexing.png"));
		indexingButton.setToolTipText(translations[104]);

		// Version 1.5
		final JMenuItem treePopupIndexingMenuItem = new JMenuItem(translations[131]);
		final JMenuItem treePopupRootsIndexingMenuItem = new JMenuItem(translations[132]);
		final JMenuItem treePopupLuceneIndexingMenuItem = new JMenuItem(translations[133]);

		final JMenuItem popupIndexingMenuItem = new JMenuItem(translations[134]);
		final JMenuItem popupRootsIndexingMenuItem = new JMenuItem(translations[135]);
		final JMenuItem popupLuceneIndexingMenuItem = new JMenuItem(translations[136]);

		if (language == lang.English)
		{
			treePopupRootsIndexingMenuItem.setEnabled(false);
			treePopupLuceneIndexingMenuItem.setEnabled(false);
			popupRootsIndexingMenuItem.setEnabled(false);
			popupLuceneIndexingMenuItem.setEnabled(false);
		}
		else
		{
			// Version 1.8
			if (language == lang.Urdu)
			{
				treePopupRootsIndexingMenuItem.setEnabled(false);
				popupRootsIndexingMenuItem.setEnabled(false);
			}
		}

		final JMenu indexingMenu = new JMenu(translations[104]);
		indexingMenu.setIcon(new ImageIcon(programFolder + "images/indexing.png"));
		indexingMenu.add(treePopupIndexingMenuItem);
		indexingMenu.add(treePopupRootsIndexingMenuItem);
		indexingMenu.add(treePopupLuceneIndexingMenuItem);

		final WebPopupMenu indexingPopupMenu = new WebPopupMenu();
		indexingPopupMenu.add(popupIndexingMenuItem);
		indexingPopupMenu.add(popupRootsIndexingMenuItem);
		indexingPopupMenu.add(popupLuceneIndexingMenuItem);
		if (language != lang.English) indexingPopupMenu.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		indexingButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
                /*
				final Component c = (Component)e.getSource();
                indexingPopupMenu.updateUI(); // Version 1.6, for getPreferredSize() to return correct value
                final Dimension dim = indexingPopupMenu.getPreferredSize();
				if(language!=lang.English)
                    indexingPopupMenu.show(c, c.getWidth()-dim.width, -dim.height);
				else
					indexingPopupMenu.show(c, 0, -dim.height);
					*/
				indexingPopupMenu.showAbove(indexingButton);
			}
		});

		final JButton exportButton = new JButton(new ImageIcon(programFolder + "images/export.png"));
		exportButton.setToolTipText(translations[105]);
		final JMenuItem exportMenuItem = new JMenuItem(translations[106], new ImageIcon(programFolder + "images/export.png"));
		final ActionListener ExportActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
				final TreePath[] treePaths = displayedTree.getSelectionPaths();
				if (treePaths != null)
				{
					final JDialog exportDialog = new JDialog(ArabicIndexer.this, translations[60], true);
					exportDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					exportDialog.setResizable(false);

					final JTextField titleTextField = new JTextField(29);
					final JPanel titlePanel = new JPanel(new BorderLayout());
					titlePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[61], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
					titlePanel.add(titleTextField, BorderLayout.CENTER);

					final JTextArea descriptionTextArea = new JTextArea(8, 0);
					final JPanel descriptionPanel = new JPanel(new BorderLayout());
					descriptionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[62], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
					descriptionPanel.add(new JScrollPane(descriptionTextArea), BorderLayout.CENTER);

					final JButton OKButton = new JButton(translations[44]);
					final JCheckBox exportWithBooksCheckBox = new JCheckBox(translations[59]);
					final ActionListener OKActionListener = new ActionListener()
					{
						public void actionPerformed(ActionEvent e)
						{
							if (titleTextField.getText().trim().isEmpty())
								JOptionPane.showOptionDialog(exportDialog.getContentPane(), translations[65], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[44]}, translations[44]);
							else
							{
								try
								{
									final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(programFolder + "temp/info"), StandardCharsets.UTF_8);
									out.write(titleTextField.getText().trim() + System.lineSeparator());
									out.write(StreamConverter(programFolder + "setting/version.txt")[1] + System.lineSeparator()); // Version 1.6
									out.write(descriptionTextArea.getText());
									out.close();

									exportDialog.dispose();

									final JFileChooser fc = new JFileChooser();
									fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
									fc.setFileFilter(new FileNameExtensionFilter(translations[66] + " (biuf)", "biuf")); // Version 1.5, Replace ExtensionFileFilter with FileNameExtensionFilter
									fc.setAcceptAllFileFilterUsed(false);
									if (language != lang.English)
										fc.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
									fc.setDialogTitle(translations[67]);

									final int returnVal = fc.showSaveDialog(ArabicIndexer.this);
									if (returnVal == JFileChooser.APPROVE_OPTION)
									{
										final File f = fc.getSelectedFile();

										// Version 1.6
										// canWrite() is buggy for Windows. TODO: Solution is Java 7 as in:
										//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4420020
										if (!f.canWrite() && !isWindows)
										{
											JOptionPane.showOptionDialog(getContentPane(), translations[74], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[84]}, translations[84]);
											return;
										}

										// Version 1.6
										// TODO: canWrite() is not working in Windows. Work Around is followed as in:
										//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6203387
										if (isWindows)
										{
											try
											{
												f.createNewFile();
												f.delete();
											}
											catch (Exception ex)
											{
												ex.printStackTrace();
												JOptionPane.showOptionDialog(getContentPane(), translations[74], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[84]}, translations[84]);
												return;
											}
										}

										final JDialog progressWindow = new JDialog(ArabicIndexer.this, translations[68], false);
										progressWindow.setLayout(new FlowLayout());
										progressWindow.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
										progressWindow.setResizable(false);

										final JProgressBar progressBar = new JProgressBar();
										progressBar.setString(translations[68]);
										progressBar.setStringPainted(true);
										progressWindow.getContentPane().add(progressBar);
										if (language != lang.English)
											progressWindow.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
										progressBar.setIndeterminate(true);
										progressWindow.pack();
										centerInScreen(progressWindow);
										progressWindow.setVisible(true);

										// Version 1.5, Disable export and delete buttons while exporting.
										editButton.setEnabled(false);
										exportButton.setEnabled(false);
										deleteButton.setEnabled(false);
										indexingButton.setEnabled(false);
										exportMenuItem.setEnabled(false);
										deleteMenuItem.setEnabled(false);
										editMenuItem.setEnabled(false);
										importMenuItem.setEnabled(false);
										indexingMenu.setEnabled(false);

										// Make the export process in a thread to not stuck the GUI.
										final Thread thread = new Thread()
										{
											public void run()
											{
												String pathName = f.toString();
												if (!(pathName.endsWith(".biuf") || pathName.endsWith(".BIUF")))
													pathName = pathName + ".biuf";

												DefaultMutableTreeNode currentNode;
												boolean exportedAllDatabase = false;
												String exportSQL = "";

												// Version 1.7
                                                /*
                                                if(authorTreeSelected)
                                                {
                                                    for(final TreePath element : treePaths)
                                                    {
                                                        currentNode = (DefaultMutableTreeNode)(element.getLastPathComponent());
                                                        if(currentNode.isRoot())
                                                        {
                                                            exportedAllDatabase = true;
                                                            break;
                                                        }

                                                        displayedTree.setSelectionPath(element);
                                                        if(addType.equals("leaf")) // i.e. Author level export
                                                        {
                                                            if(exportSQL.isEmpty())
                                                                exportSQL = "author = '" + bookAuthor +"'";
                                                            else
                                                                exportSQL = exportSQL + " OR " + "author = '" + bookAuthor +"'";
                                                        }
                                                        else
                                                        {
                                                            if(addType.equals("parent")) // i.e. Multi-volume Books level export.
                                                            {
                                                                if(exportSQL.isEmpty())
                                                                    exportSQL = "(parent = '" + bookName + "' AND author = '" + bookAuthor + "')";
                                                                else
                                                                    exportSQL = exportSQL + " OR (parent = '" + bookName + "' AND author = '" + bookAuthor + "')";
                                                            }
                                                            else // i.e. singe-volume Books level export
                                                            {
                                                                if(exportSQL.isEmpty())
                                                                    exportSQL = "path = '" + bookPath + "'";
                                                                else
                                                                    exportSQL = exportSQL + " OR path = '" + bookPath + "'";
                                                            }
                                                        }
                                                    }
                                                }
                                                else
                                                */
												{
													for (final TreePath element : treePaths)
													{
														currentNode = (DefaultMutableTreeNode) (element.getLastPathComponent());
														if (currentNode.isRoot())
														{
															exportedAllDatabase = true;
															break;
														}

														displayedTree.setSelectionPath(element);
														if (addType.equals("leaf")) // i.e. Section level export
														{
															if (authorTreeSelected)
															{
																if (exportSQL.isEmpty())
																	exportSQL = "author = '" + bookAuthor + "'";
																else
																	exportSQL = exportSQL + " OR " + "author = '" + bookAuthor + "'";
															}
															else
															{
																if (exportSQL.isEmpty())
																	exportSQL = "category = '" + bookCategory + "'";
																else
																	exportSQL = exportSQL + " OR " + "category = '" + bookCategory + "'";
															}
														}
														else
														{
															if (addType.equals("parent")) // i.e. Multi-volume Books level export.
															{
																if (exportSQL.isEmpty())
																	exportSQL = "(parent = '" + bookName + "' AND category = '" + bookCategory + "' AND author = '" + bookAuthor + "')"; // Version 1.7, ... AND author = ...
																else
																	exportSQL = exportSQL + " OR (parent = '" + bookName + "' AND category = '" + bookCategory + "' AND author = '" + bookAuthor + "')";
															}
															else // i.e. singe-volume Books level export
															{
																if (exportSQL.isEmpty())
																	exportSQL = "id = '" + bookId + "'"; // Version 2.1
																else
																	exportSQL = exportSQL + " OR id = '" + bookId + "'";
															}
														}
													}
												}

												// Trying to export Parent tree node (base reference) i.e. The whole database
												if (exportedAllDatabase)
													exportSQL = "SELECT * FROM " + bookTableName;
												else
													exportSQL = "SELECT * FROM " + bookTableName + " WHERE " + exportSQL;

												try
												{
													// These are the files to include in the ZIP file
													final Vector<String> paths = new Vector<>();
													//final Vector<String> absoluteIndexingFilesNames = new Vector<>(); // Version 2.1, removed. txt will be named using idVector instead since it is unique

													/*
													 * These variables are used to store the whole export file,
													 * if the database was so big (rarely happens) we can push the rows directly to the file
													 * (but we will lose the tracking the indexing files) and then delete it if repeated paths,
													 * otherwise attend the absolute file names.
													 */
													final Vector<String> nameVector = new Vector<>();
													final Vector<String> parentVector = new Vector<>();
													final Vector<Integer> idVector = new Vector<>();
													final Vector<String> categoryVector = new Vector<>();
													final Vector<String> authorVector = new Vector<>(); // Version 1.6
													final Vector<String> absoluteFileNameVector = new Vector<>();

													final Statement stmt = sharedDBConnection.createStatement();
													final ResultSet rs1 = stmt.executeQuery(exportSQL);

													while (rs1.next())
													{
														final String pathTemp = rs1.getString("path"); // for speed instead of rs.getString("path") each time (5 times)
														paths.add(pathTemp);
														absoluteFileNameVector.add(pathTemp.substring(pathTemp.lastIndexOf(File.separator) + 1));
														//absoluteIndexingFilesNames.add(pathTemp.substring(pathTemp.lastIndexOf(File.separator) + 1) + ".txt");
														nameVector.add(rs1.getString("name"));
														parentVector.add(rs1.getString("parent"));
														categoryVector.add(rs1.getString("category"));
														authorVector.add(rs1.getString("author"));
														idVector.add(rs1.getInt("id"));
													}
													rs1.close();

													// Checking if there are repeated book files.
													/* Version 2.1, no need since the DB is not allowing repeated books now that has the same author, name, part, path
													String repeatedFileNames = null;
													for (int i = 0; i < absoluteIndexingFilesNames.size() && repeatedFileNames == null; i++)
													{
														for (int j = i + 1; j < absoluteIndexingFilesNames.size(); j++)
														{
															if (absoluteIndexingFilesNames.elementAt(i).equals(absoluteIndexingFilesNames.elementAt(j)))
															{
																repeatedFileNames = '[' + categoryVector.elementAt(i) + '|' + authorVector.elementAt(i) + ", " + (parentVector.elementAt(i).isEmpty() ? "" : (parentVector.elementAt(i) + ", ")) + nameVector.elementAt(i) + "][" + paths.elementAt(i) + ']' +
																		System.lineSeparator() + '[' + categoryVector.elementAt(j) + '|' + authorVector.elementAt(j) + ", " + (parentVector.elementAt(j).isEmpty() ? "" : (parentVector.elementAt(j) + ", ")) + nameVector.elementAt(j) + "][" + paths.elementAt(j) + ']';
																break;
															}
														}
													}

													if (repeatedFileNames != null)
														JOptionPane.showOptionDialog(progressWindow.getContentPane(), translations[69] + System.lineSeparator() + repeatedFileNames, translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
													else
													*/
													{
														// Created export DB file.
														final OutputStreamWriter outTable = new OutputStreamWriter(new FileOutputStream(programFolder + "temp/" + bookTableName), StandardCharsets.UTF_8);
														for (int i = 0; i < absoluteFileNameVector.size() - 1; i++)
															outTable.write(nameVector.elementAt(i) + 'ö' + parentVector.elementAt(i) + 'ö' + categoryVector.elementAt(i) + 'ö' + authorVector.elementAt(i) + 'ö' + absoluteFileNameVector.elementAt(i) + 'ö' + idVector.elementAt(i) + ".txt" + System.lineSeparator()); // Version 2.1

														final int lastIndex = absoluteFileNameVector.size() - 1;

														// This is to avoid adding new line at the end of the file
														outTable.write(nameVector.elementAt(lastIndex) + 'ö' + parentVector.elementAt(lastIndex) + 'ö' + categoryVector.elementAt(lastIndex) + 'ö' + authorVector.elementAt(lastIndex) + 'ö' + absoluteFileNameVector.elementAt(lastIndex) + 'ö' + idVector.elementAt(lastIndex) + ".txt");
														outTable.close();

														/*
														indexingFiles.add("temp" + fileSeparator + bookTableName);
														absoluteIndexingFilesNames.add(bookTableName);

														indexingFiles.add("temp" + fileSeparator + "info");
														absoluteIndexingFilesNames.add("info");
														*/

														// Create a buffer for reading the files.
														final byte[] buf = new byte[1024];

														// Create the ZIP file
														final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(pathName));

														// Adding 'arabicDatabase' or 'englishDatabase' to the zip file.
														FileInputStream in = new FileInputStream(programFolder + "temp/" + bookTableName);

														// Add ZIP entry to output stream.
														out.putNextEntry(new ZipEntry(bookTableName));

														// Transfer bytes from the file to the ZIP file.
														int len;
														while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

														// Complete the entry
														out.closeEntry();

														// Adding 'info' to the zip file.
														in = new FileInputStream(programFolder + "temp/info");
														out.putNextEntry(new ZipEntry("info"));
														while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
														out.closeEntry();
														in.close();

														// Compress the indexing files and adding them to the exported file.
														//for (int i = 0; i < paths.size(); i++) // Version 2.1
														for (int i = 0; i < idVector.size(); i++)
														{
															final ResultSet rs2 = stmt.executeQuery("SELECT MAX(Page) AS size FROM b" + idVector.elementAt(i));
															if (rs2.next())
															{
																final int size = rs2.getInt("size");
																final Vector<String> pages = new Vector<>();
																pages.setSize(size);
																final ResultSet rs3 = stmt.executeQuery("SELECT * FROM b" + idVector.elementAt(i));
																while (rs3.next())
																	pages.set(rs3.getInt("page") - 1, rs3.getString("Content"));
																rs3.close();

																final OutputStreamWriter out1 = new OutputStreamWriter(new FileOutputStream(programFolder + "temp/" + idVector.elementAt(i) + ".txt"), StandardCharsets.UTF_8);
																for (int q = 0; q < pages.size(); q++)
																{
																	if (pages.elementAt(q) != null)
																		out1.write(pages.elementAt(q) + System.lineSeparator());
																	out1.write("öööööö " + (q + 1) + " öööööö" + System.lineSeparator());
																}
																out1.close();

																in = new FileInputStream(programFolder + "temp/" + idVector.elementAt(i) + ".txt");
																out.putNextEntry(new ZipEntry(idVector.elementAt(i) + ".txt"));
																while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
																out.closeEntry();
																in.close();
															}
															rs2.close();
														}

														// Used for notifying the user for the deleted files.
														String deletedDocuments = "";

														// Compress the PDF documents and adding them to the exported file.
														if (exportWithBooksCheckBox.isSelected())
														{
															final HashSet<String> zipFilesList = new HashSet<>();
															for (int i = 0; i < paths.size(); i++)
															{
																final String path = paths.elementAt(i).replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9

																// Check the indexing files if they are exists.
																if (new File(path).exists())
																{
																	// Version 1.3, Adding Manuscript folder to the ZIP file.
																	if (new File(path).isDirectory())
																	{
																		final File[] files = new File(path).listFiles();
																		if(files != null)
																		{
																			for (File f : files)
																			{
																				in = new FileInputStream(path + '/' + f.getName());
																				out.putNextEntry(new ZipEntry(absoluteFileNameVector.elementAt(i) + '/' + f.getName()));
																				while ((len = in.read(buf)) > 0)
																					out.write(buf, 0, len);
																				out.closeEntry();
																				in.close();
																			}
																		}
																	}
																	else
																	{
																		if(zipFilesList.add(absoluteFileNameVector.elementAt(i))) // Version 2.1, to avoid adding repeated pdf files. it will throw exception
																		{
																			in = new FileInputStream(path);
																			out.putNextEntry(new ZipEntry(absoluteFileNameVector.elementAt(i)));
																			while ((len = in.read(buf)) > 0)
																				out.write(buf, 0, len);
																			out.closeEntry();
																			in.close();
																		}
																	}
																}
																else
																	deletedDocuments = deletedDocuments + System.lineSeparator() + '[' + categoryVector.elementAt(i) + ", " + (parentVector.elementAt(i).isEmpty() ? "" : (parentVector.elementAt(i) + ", ")) + nameVector.elementAt(i) + "][" + paths.elementAt(i) + ']';
															}
														}

														// Complete the ZIP file
														out.close();

														if (!deletedDocuments.isEmpty())
															JOptionPane.showOptionDialog(progressWindow.getContentPane(), getOptionPaneScrollablePanel(translations[75], deletedDocuments.substring(System.lineSeparator().length())), translations[3], JOptionPane.YES_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[44]}, translations[44]);
													}
													stmt.close();
												}
												catch (Exception e)
												{
													e.printStackTrace();
												}

												// Clear the temp folder
												final File[] deletedFiles = new File(programFolder + "temp/").listFiles(); // Version 1.5
												if (deletedFiles != null)
													for (File element : deletedFiles)
														element.delete();

												progressWindow.dispose();

												// Version 1.5
												editButton.setEnabled(true);
												exportButton.setEnabled(true);
												deleteButton.setEnabled(true);
												indexingButton.setEnabled(true);
												exportMenuItem.setEnabled(true);
												deleteMenuItem.setEnabled(true);
												editMenuItem.setEnabled(true);
												importMenuItem.setEnabled(true);
												indexingMenu.setEnabled(true);
											}
										};
										thread.start();
									}
								}
								catch (Exception ex)
								{
									ex.printStackTrace();
								}
							}
						}
					};
					OKButton.addActionListener(OKActionListener);
					titleTextField.addActionListener(OKActionListener);

					final JPanel decoratePanel = new JPanel(new BorderLayout());
					decoratePanel.add(exportWithBooksCheckBox, BorderLayout.EAST);
					decoratePanel.add(OKButton, BorderLayout.WEST);

					exportDialog.add(decoratePanel, BorderLayout.SOUTH);
					exportDialog.add(descriptionPanel, BorderLayout.CENTER);
					exportDialog.add(titlePanel, BorderLayout.NORTH);

					exportDialog.pack();
					if (language != lang.English)
						exportDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
					centerInScreen(exportDialog);
					exportDialog.setVisible(true);
				}
				else
					JOptionPane.showOptionDialog(getContentPane(), translations[63], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
			}
		};
		exportButton.addActionListener(ExportActionListener);
		exportMenuItem.addActionListener(ExportActionListener);

		final ActionListener IndexingActionListener = new ActionListener()
		{
			public void actionPerformed(final ActionEvent e)
			{
				final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
				final TreePath[] treePaths = displayedTree.getSelectionPaths();
				if (treePaths != null)
				{
					if (leafNode && rootNode)
						JOptionPane.showOptionDialog(getContentPane(), translations[41], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
					else
					{
						try
						{
							// Version 1.5, Disable export and delete buttons while indexing.
							editButton.setEnabled(false);
							exportButton.setEnabled(false);
							deleteButton.setEnabled(false);
							exportMenuItem.setEnabled(false);
							deleteMenuItem.setEnabled(false);
							editMenuItem.setEnabled(false);
							importMenuItem.setEnabled(false);

							IndexSearcher searcher;
							String indexingProgressTitle;
							if (e.getSource().equals(treePopupIndexingMenuItem) || e.getSource().equals(popupIndexingMenuItem))
							{
								treePopupIndexingMenuItem.setEnabled(false);
								popupIndexingMenuItem.setEnabled(false);
								indexingProgressTitle = translations[134];
								searcher = defaultSearcher;
								//writer = indexWriter;
							}
							else
							{
								if (e.getSource().equals(treePopupRootsIndexingMenuItem) || e.getSource().equals(popupRootsIndexingMenuItem))
								{
									treePopupRootsIndexingMenuItem.setEnabled(false);
									popupRootsIndexingMenuItem.setEnabled(false);
									indexingProgressTitle = translations[135];
									searcher = arabicRootsSearcher;
									//writer = arabicRootsWriter;
								}
								else
								{
									treePopupLuceneIndexingMenuItem.setEnabled(false);
									popupLuceneIndexingMenuItem.setEnabled(false);
									indexingProgressTitle = translations[136];
									searcher = arabicLuceneSearcher;
									//writer = arabicLuceneWriter;
								}
							}

							// Version 1.6, writer should be local. global will prevent parallel indexing since the same writer will be assign many times causing duplicate indexing.
							final IndexWriter writer = indexingProgressTitle.equals(translations[134]) ? indexWriter : (indexingProgressTitle.equals(translations[135]) ? arabicRootsWriter : arabicLuceneWriter);

							final Statement stmt = sharedDBConnection.createStatement();

							final Vector<String> bookPathVector = new Vector<>();
							final Vector<Integer> bookIdVector = new Vector<>();
							final Vector<String> bookNameVector = new Vector<>();
							final Vector<String> bookCategoryVector = new Vector<>();
							final Vector<String> bookAuthorVector = new Vector<>(); // Version 1.7
							final Vector<String> bookParentVector = new Vector<>();
							final Vector<Integer> repeatedIndexedBookIdVector = new Vector<>(); // Version 2.1

							for (TreePath element : treePaths)
							{
								displayedTree.setSelectionPath(element);
								if (leafNode)
								{
									// To remove the repetition that happens when selecting a category.
									if (!bookPathVector.contains(bookPath))
									{
										bookPathVector.addElement(bookPath);
										bookNameVector.addElement(bookName);
										bookIdVector.addElement(bookId);
										bookAuthorVector.addElement(bookAuthor);
										bookCategoryVector.addElement(bookCategory);
										bookParentVector.addElement(bookParentName);
									}
								}
								else
								{
									if (bookPath.equals("treeRoot") || bookPath.equals("authorTreeRoot"))
									{
										bookPathVector.removeAllElements();
										bookNameVector.removeAllElements();
										bookAuthorVector.removeAllElements();
										bookIdVector.removeAllElements();
										bookCategoryVector.removeAllElements();
										bookParentVector.removeAllElements();

										final ResultSet rs = stmt.executeQuery("SELECT * FROM " + bookTableName);
										while (rs.next())
										{
											bookPathVector.addElement(rs.getString("path"));
											bookNameVector.addElement(rs.getString("name"));
											bookAuthorVector.addElement(rs.getString("author"));
											bookIdVector.addElement(rs.getInt("id"));
											bookCategoryVector.addElement(rs.getString("category"));
											bookParentVector.addElement(rs.getString("parent"));
										}
										rs.close();

										// No need to continue.
										break;
									}
									else
									{
										if (bookPath.equals("category"))
										{
											final ResultSet rs = stmt.executeQuery("SELECT * FROM " + bookTableName + " WHERE category = '" + bookCategory + "'");
											while (rs.next())
											{
												if (!bookPathVector.contains(rs.getString("path")))
												{
													bookPathVector.addElement(rs.getString("path"));
													bookNameVector.addElement(rs.getString("name"));
													bookIdVector.addElement(rs.getInt("id"));
													bookAuthorVector.addElement(rs.getString("author"));
													bookCategoryVector.addElement(bookCategory); // Version 1.7
													bookParentVector.addElement(rs.getString("parent"));
												}
											}
											rs.close();
										}
										else
										{
											if (bookPath.equals("author"))
											{
												final ResultSet rs = stmt.executeQuery("SELECT * FROM " + bookTableName + " WHERE author = '" + bookAuthor + "'");
												while (rs.next())
												{
													if (!bookPathVector.contains(rs.getString("path")))
													{
														bookPathVector.addElement(rs.getString("path"));
														bookNameVector.addElement(rs.getString("name"));
														bookIdVector.addElement(rs.getInt("id"));
														bookAuthorVector.addElement(bookAuthor);
														bookCategoryVector.addElement(rs.getString("category"));
														bookParentVector.addElement(rs.getString("parent"));
													}
												}
												rs.close();
											}
											else // i.e. bookPath.equals("parent")
											{
												//if(authorTreeSelected) // Version 1.7
												{
													final ResultSet rs = stmt.executeQuery("SELECT * FROM " + bookTableName + " WHERE author = '" + bookAuthor + "' AND parent = '" + bookName + "' AND category = '" + bookCategory + "'"); // Version 1.7, ... AND category = ...
													while (rs.next())
													{
														if (!bookPathVector.contains(rs.getString("path")))
														{
															bookPathVector.addElement(rs.getString("path"));
															bookNameVector.addElement(rs.getString("name"));
															bookIdVector.addElement(rs.getInt("id"));
															bookAuthorVector.addElement(bookAuthor);
															bookCategoryVector.addElement(bookCategory /*rs.getString("category")*/);
															bookParentVector.addElement(bookName);
														}
													}
													rs.close();
												}
                                                /*
                                                else
                                                {
                                                    final ResultSet rs = stmt.executeQuery("SELECT * FROM "+bookTableName+" WHERE category = '"+bookCategory+"' AND parent = '"+bookName+"'");
                                                    while(rs.next())
                                                    {
                                                        if(bookPathVector.indexOf(rs.getString("path"))==-1)
                                                        {
                                                            bookPathVector.addElement(rs.getString("path"));
                                                            bookNameVector.addElement(rs.getString("name"));
                                                            bookAuthorVector.addElement(rs.getString("author"));
                                                            bookCategoryVector.addElement(bookCategory);
                                                            bookParentVector.addElement(bookName);
                                                        }
                                                    }
                                                    rs.close();
                                                }
                                                */
											}
										}
									}
								}
							}

							stmt.close();
							String indexedItemsString = "";

							// To check if this book is already indexed or not.
							//final QueryParser queryParser = new QueryParser("path", new KeywordAnalyzer()); // Version 1.9, replaced

							for (int i = 0; i < bookIdVector.size(); i++) // Version 2.1
							{
                                /* version 1.9, replaced with the next
								//bits.clear();
                                final org.apache.lucene.util.FixedBitSet bits = new org.apache.lucene.util.FixedBitSet(searcher.getIndexReader().maxDoc()); // Version 1.7

								// Version 1.4
								searcher.search(queryParser.parse('\"' + bookPathVector.elementAt(i).replaceAll("\\\\", "\\\\\\\\") + '\"'), new SimpleCollector()
                                {
                                    public int docBase;
                                    public void collect(int doc) {bits.set(doc + docBase);}
                                    public void doSetNextReader(LeafReaderContext context){this.docBase = context.docBase;}
                                    public boolean needsScores(){return false;};
                                });
                                */

								final TopDocs results = searcher.search(new TermQuery(new Term("id", bookIdVector.elementAt(i).toString())), 1); // Version 2.1

								//if(bits.length()!=0)
								if (results.totalHits.value != 0)
								{
									// To get the tree path of the indexed book.
									TreePath treePath = null;

									if (authorTreeSelected) // Version 1.7
									{
										final Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) authorTreeModel.getRoot()).postorderEnumeration();
										while (nodes.hasMoreElements())
										{
											final DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
											if (node.toString().equals(bookNameVector.elementAt(i)))
											{
												if (!bookParentVector.elementAt(i).isEmpty())
												{
													if (node.getParent().toString().equals(bookParentVector.elementAt(i)))
													{
														if (node.getParent().getParent().toString().equals(bookAuthorVector.elementAt(i)))
														{
															treePath = new TreePath(node.getPath());
															break;
														}
													}
												}
												else
												{
													if (node.getParent().toString().equals(bookAuthorVector.elementAt(i)))
													{
														treePath = new TreePath(node.getPath());
														break;
													}
												}
											}
										}
									}
									else
									{
										final Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) treeModel.getRoot()).postorderEnumeration();
										while (nodes.hasMoreElements())
										{
											final DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
											if (node.toString().equals(bookNameVector.elementAt(i)))
											{
												if (!bookParentVector.elementAt(i).isEmpty())
												{
													if (node.getParent().toString().equals(bookParentVector.elementAt(i)))
														if (node.getParent().getParent().toString().equals(bookCategoryVector.elementAt(i)))
														{
															treePath = new TreePath(node.getPath());
															break;
														}
												}
												else
												{
													if (node.getParent().toString().equals(bookCategoryVector.elementAt(i)))
													{
														treePath = new TreePath(node.getPath());
														break;
													}
												}
											}
										}
									}

									indexedItemsString = indexedItemsString + System.lineSeparator() + treePath;
									repeatedIndexedBookIdVector.addElement(bookIdVector.elementAt(i));
								}

							    /*
								// e.g. QueryParser.escape(bookPath) OR ( "\"" + (windows? (bookPath.replaceAll("\\\\", "\\\\\\\\").replaceAll(":", "\\\\:")):bookPath) + "\""); //replace \ to \\ to work in lucene (you need to x 4 each one !!!, Character.valueOf('\\').toString()
							    final Hits hits = defaultSearcher.search(queryParser.parse("\"" + bookPathVector.elementAt(i).replaceAll("\\\\", "\\\\\\\\") + "\""));
							    if(hits.length() > 0)
							    {
							    	// To get the tree path of the indexed book.
							    	TreePath treePath = null;
							    	final java.util.Enumeration nodes = ((DefaultMutableTreeNode)treeModel.getRoot()).postorderEnumeration();
									while(nodes.hasMoreElements())
									{
										final DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes.nextElement();
										if(node.toString().equals(bookNameVector.elementAt(i)))
										{
											if(!bookParentVector.elementAt(i).equals(""))
											{
												if(node.getParent().toString().equals(bookParentVector.elementAt(i)))
													if(node.getParent().getParent().toString().equals(bookCategoryVector.elementAt(i)))
													{
														treePath = new TreePath(node.getPath());
														break;
													}
											}
											else
											{
												if(node.getParent().toString().equals(bookCategoryVector.elementAt(i)))
												{
													treePath = new TreePath(node.getPath());
													break;
												}
											}
										}
									}

							    	indexedItemsString = indexedItemsString + lineSeparator + treePath;
							    	repeatedIndexedBookPathVector.addElement(bookPathVector.elementAt(i));
							    }
							    */
							}

							// Version 1.5, AtomicBoolean instead of global boolean since we can declare it as final (for inner access by threads) and can be set.
							final AtomicBoolean stopIndexing = new AtomicBoolean();

							final JDialog indexingProgressWindow = new JDialog(ArabicIndexer.this, indexingProgressTitle/*((JMenuItem)e.getSource()).getText()*/, false);
							indexingProgressWindow.getContentPane().setLayout(new BoxLayout(indexingProgressWindow.getContentPane(), BoxLayout.Y_AXIS));
							indexingProgressWindow.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
							indexingProgressWindow.addWindowListener(new WindowAdapter()
							{
								public void windowClosing(WindowEvent e)
								{
									stopIndexing.set(true);
								}
							});
							indexingProgressWindow.setResizable(false);
							indexingProgressWindow.setSize(350, 160);

							final JProgressBar bookProgressBar = new JProgressBar(0, bookPathVector.size());
							final JProgressBar pageProgressBar = new JProgressBar();
							final JLabel indexingProgressWindowLabel = new JLabel(translations[98]);

							final JButton exitButton = new JButton(translations[73]);
							exitButton.addActionListener(new ActionListener()
							{
								public void actionPerformed(ActionEvent e)
								{
									stopIndexing.set(true);
								}
							});

							final JPanel decoratePanel1 = new JPanel(new FlowLayout(language != lang.English ? FlowLayout.RIGHT : FlowLayout.LEFT));
							decoratePanel1.add(indexingProgressWindowLabel);

							final JPanel decoratePanel2 = new JPanel(new FlowLayout());
							decoratePanel2.add(exitButton);

							indexingProgressWindow.add(decoratePanel1);
							indexingProgressWindow.add(pageProgressBar);
							indexingProgressWindow.add(bookProgressBar);
							indexingProgressWindow.add(decoratePanel2);

							//indexingProgressWindow.setBounds(screenSize.width/2-140, screenSize.height/2-30, 280, 67);
							pageProgressBar.setStringPainted(true);
							bookProgressBar.setStringPainted(true);

							if (language != lang.English)
								indexingProgressWindow.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

							indexingProgressWindow.pack();
							centerInScreen(indexingProgressWindow);

							if (!repeatedIndexedBookIdVector.isEmpty())
							{
								// Version 1.3, Enable scrolling in JOptionPane when it exceeds screen limit using getOptionPaneScrollablePanel().
								if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(indexingProgressWindow.getContentPane(), getOptionPaneScrollablePanel(translations[39], indexedItemsString.substring(System.lineSeparator().length())), translations[3], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{translations[0], translations[1]}, translations[0]))
								{
									final Thread thread = new Thread()
									{
										public void run()
										{
											try
											{
												//System.out.println(indexReader.docCount() + " docs before delete in index");
												for (int element : repeatedIndexedBookIdVector) // Version 2.1
													//System.out.println("Deleted: "+indexReader.deleteDocuments(new Term("path", repeatedIndexedBookPathVector.elementAt(i))));// by default it will consider it as one world because term is one world.
													writer.deleteDocuments(new Term("id", String.valueOf(element)));

												writer.commit(); // Version 1.6

												for (int i = 0; i < bookPathVector.size() && !stopIndexing.get(); i++)
												{
													final String bookTreePath = '[' + (authorTreeSelected ? bookAuthorVector.elementAt(i) : bookCategoryVector.elementAt(i)) + (bookParentVector.elementAt(i).isEmpty() ? "" : (", " + bookParentVector.elementAt(i))) + ", " + bookNameVector.elementAt(i) + ']';
													indexingProgressWindowLabel.setText(bookTreePath);

													// Add this book to the index
													addDocumentToIndex(bookNameVector.elementAt(i), bookPathVector.elementAt(i), bookCategoryVector.elementAt(i), bookAuthorVector.elementAt(i), bookParentVector.elementAt(i), pageProgressBar, writer, bookIdVector.elementAt(i));
													bookProgressBar.setValue(i + 1);
												}
											}
											catch (Exception e)
											{
												e.printStackTrace();
											}

											reopenIndexSearcher();
											indexingProgressWindow.dispose();

											// Version 1.5, Enable export and delete buttons after indexing.
											editButton.setEnabled(true);
											exportButton.setEnabled(true);
											deleteButton.setEnabled(true);
											exportMenuItem.setEnabled(true);
											deleteMenuItem.setEnabled(true);
											editMenuItem.setEnabled(true);
											importMenuItem.setEnabled(true);

											if (e.getSource().equals(treePopupIndexingMenuItem) || e.getSource().equals(popupIndexingMenuItem))
											{
												treePopupIndexingMenuItem.setEnabled(true);
												popupIndexingMenuItem.setEnabled(true);
											}
											else
											{
												if (e.getSource().equals(treePopupRootsIndexingMenuItem) || e.getSource().equals(popupRootsIndexingMenuItem))
												{
													treePopupRootsIndexingMenuItem.setEnabled(true);
													popupRootsIndexingMenuItem.setEnabled(true);
												}
												else
												{
													treePopupLuceneIndexingMenuItem.setEnabled(true);
													popupLuceneIndexingMenuItem.setEnabled(true);
												}
											}
										}
									};
									thread.start();
								}
								else
								{
									final Thread thread = new Thread()
									{
										public void run()
										{
											for (int i = 0; i < bookIdVector.size() && !stopIndexing.get(); i++)
											{
												if (!repeatedIndexedBookIdVector.contains(bookIdVector.elementAt(i)))
												{
													final String bookTreePath = '[' + (authorTreeSelected ? bookAuthorVector.elementAt(i) : bookCategoryVector.elementAt(i)) + (bookParentVector.elementAt(i).isEmpty() ? "" : (", " + bookParentVector.elementAt(i))) + ", " + bookNameVector.elementAt(i) + ']';
													indexingProgressWindowLabel.setText(bookTreePath);

													// Add this book to the index
													addDocumentToIndex(bookNameVector.elementAt(i), bookPathVector.elementAt(i), bookCategoryVector.elementAt(i), bookAuthorVector.elementAt(i), bookParentVector.elementAt(i), pageProgressBar, writer, bookIdVector.elementAt(i));
													bookProgressBar.setValue(i + 1);
												}
											}

											reopenIndexSearcher();
											indexingProgressWindow.dispose();

											// Version 1.5, Enable export and delete buttons after indexing.
											editButton.setEnabled(true);
											exportButton.setEnabled(true);
											deleteButton.setEnabled(true);
											exportMenuItem.setEnabled(true);
											deleteMenuItem.setEnabled(true);
											editMenuItem.setEnabled(true);
											importMenuItem.setEnabled(true);

											if (e.getSource().equals(treePopupIndexingMenuItem) || e.getSource().equals(popupIndexingMenuItem))
											{
												treePopupIndexingMenuItem.setEnabled(true);
												popupIndexingMenuItem.setEnabled(true);
											}
											else
											{
												if (e.getSource().equals(treePopupRootsIndexingMenuItem) || e.getSource().equals(popupRootsIndexingMenuItem))
												{
													treePopupRootsIndexingMenuItem.setEnabled(true);
													popupRootsIndexingMenuItem.setEnabled(true);
												}
												else
												{
													treePopupLuceneIndexingMenuItem.setEnabled(true);
													popupLuceneIndexingMenuItem.setEnabled(true);
												}
											}
										}
									};
									thread.start();
								}
							}
							else
							{
								final Thread thread = new Thread()
								{
									public void run()
									{
										for (int i = 0; i < bookIdVector.size() && !stopIndexing.get(); i++)
										{
											final String bookTreePath = '[' + (authorTreeSelected ? bookAuthorVector.elementAt(i) : bookCategoryVector.elementAt(i)) + (bookParentVector.elementAt(i).isEmpty() ? "" : (", " + bookParentVector.elementAt(i))) + ", " + bookNameVector.elementAt(i) + ']';
											indexingProgressWindowLabel.setText(bookTreePath);

											// Add this book to the index
											addDocumentToIndex(bookNameVector.elementAt(i), bookPathVector.elementAt(i), bookCategoryVector.elementAt(i), bookAuthorVector.elementAt(i), bookParentVector.elementAt(i), pageProgressBar, writer, bookIdVector.elementAt(i));
											bookProgressBar.setValue(i + 1);
										}

										reopenIndexSearcher();
										indexingProgressWindow.dispose();

										// Version 1.5, Enable export and delete buttons after indexing.
										editButton.setEnabled(true);
										exportButton.setEnabled(true);
										deleteButton.setEnabled(true);
										exportMenuItem.setEnabled(true);
										deleteMenuItem.setEnabled(true);
										editMenuItem.setEnabled(true);
										importMenuItem.setEnabled(true);

										if (e.getSource().equals(treePopupIndexingMenuItem) || e.getSource().equals(popupIndexingMenuItem))
										{
											treePopupIndexingMenuItem.setEnabled(true);
											popupIndexingMenuItem.setEnabled(true);
										}
										else
										{
											if (e.getSource().equals(treePopupRootsIndexingMenuItem) || e.getSource().equals(popupRootsIndexingMenuItem))
											{
												treePopupRootsIndexingMenuItem.setEnabled(true);
												popupRootsIndexingMenuItem.setEnabled(true);
											}
											else
											{
												treePopupLuceneIndexingMenuItem.setEnabled(true);
												popupLuceneIndexingMenuItem.setEnabled(true);
											}
										}
									}
								};
								thread.start();
							}
							indexingProgressWindow.setVisible(true);
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
					}
				}
				else
					JOptionPane.showOptionDialog(getContentPane(), translations[38], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
			}
		};
		treePopupIndexingMenuItem.addActionListener(IndexingActionListener);
		treePopupRootsIndexingMenuItem.addActionListener(IndexingActionListener);
		treePopupLuceneIndexingMenuItem.addActionListener(IndexingActionListener);
		popupIndexingMenuItem.addActionListener(IndexingActionListener);
		popupRootsIndexingMenuItem.addActionListener(IndexingActionListener);
		popupLuceneIndexingMenuItem.addActionListener(IndexingActionListener);

		final JPopupMenu treePopupMenu = new JPopupMenu();
		treePopupMenu.add(addMenuItem);
		treePopupMenu.add(editMenuItem);
		treePopupMenu.add(deleteMenuItem);
		treePopupMenu.add(exportMenuItem);
		treePopupMenu.add(indexingMenu);
		if (language != lang.English) treePopupMenu.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		final AtomicBoolean fireTreeSelectionListener = new AtomicBoolean(true);
		final TreeSelectionListener treeSelectionListener = new TreeSelectionListener()
		{
			public void valueChanged(TreeSelectionEvent e)
			{
				leafNode = false;
				rootNode = false;
				bookName = "";
				bookPath = "";
				bookId = 0;
				bookAbsolutePath = "";
				bookCategory = "";
				bookAuthor = "";
				addType = "";
				bookParentName = ""; // Version 1.7
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (authorTreeSelected ? authorTree : tree).getLastSelectedPathComponent();

				fireSearchListSelectionListener = false;

				if (node == null) return;
				final Object nodeInfo = node.getUserObject();
				if (node.isLeaf())
				{
					leafNode = true;
					if (node.isRoot())
					{
						rootNode = true;
						addType = authorTreeSelected ? "author" : "category";
					}
					else
					{
						final NodeInfo bookNode = (NodeInfo) nodeInfo;
						bookName = bookNode.name;
						bookPath = bookNode.path;
						bookAbsolutePath = bookNode.absolutePath;
						bookCategory = bookNode.category;
						bookParentName = bookNode.parent;
						bookAuthor = bookNode.author;
						bookId = bookNode.id;

						// Version 1.6
						enableEditPanel(true, translations[70]);
						if (fireTreeSelectionListener.get())
						{
							try
							{
								final Statement stmt = sharedDBConnection.createStatement();
								ResultSet rs = stmt.executeQuery("SELECT * FROM b" + bookId + " WHERE page=(SELECT MIN(page) FROM b" + bookId + ")"); // Version 2.1
								if (rs.next())
								{
									// Version 1.7
									final Reader description = rs.getCharacterStream("content");
									final char[] arr = new char[4 * 1024]; // 4K at a time
									final StringBuilder buf = new StringBuilder();
									int numChars;

									while ((numChars = description.read(arr, 0, arr.length)) > 0)
										buf.append(arr, 0, numChars);

									setText(buf.toString(), "text/plain", programFolder + "images/save.png", translations[70]); // Version 1.7
									displayEditTextPane.setCaretPosition(0);

									pageTextField.setText(rs.getString("page"));
									currentDisplayedPage = pageTextField.getText();
								}
								else
									setText("<font color=red>خطأ، الملف لا يتضمن أية صفحة.\nيمكن أن يحدث هذا إن قمت بتحرير هذا الكتاب الذي قمت باستيراده بشكل يدوي وكان ملف الفهرسة فيه خاليا.</font>", "text/html", programFolder + "images/edit.png", translations[103]);

								rs = stmt.executeQuery("SELECT MAX(Page) FROM b" + bookId); // There should be one page at least.
								rs.next();
								pagesTextField.setText(rs.getString(1));

								stmt.close();
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
							}
						}
					}
				}
				else
				{
					enableEditPanel(false, translations[70]);
					if (node.isRoot())
						rootNode = true;

					final NodeInfo bookNode = (NodeInfo) nodeInfo;
					bookPath = bookNode.path;
					bookId = bookNode.id;
					bookAbsolutePath = bookNode.absolutePath;
					if (bookPath.equals("treeRoot"))
						addType = "category";
					else
					{
						if (bookPath.equals("authorTreeRoot"))
							addType = "author";
						else
						{
							if (bookPath.equals("parent"))
							{
								addType = "parent";// i.e. you will add one volume to a multi-volume book
								bookName = bookNode.name;
								bookCategory = bookNode.category;
								bookAuthor = bookNode.author;
							}
							else
							{
								addType = "leaf"; //bookPath.equals("category") || "author"
								if (authorTreeSelected)
									bookAuthor = bookNode.name;
								else
									bookCategory = bookNode.name; // name in this case is the category
							}
						}
					}
				}
			}
		};
		tree.addTreeSelectionListener(treeSelectionListener);
		authorTree.addTreeSelectionListener(treeSelectionListener);

		// Version 1.5, MouseAdapter() instead of MouseListener() since you don't need to override all the methods.
		final MouseAdapter mouseAdapter = new MouseAdapter()
		{
			public void mouseClicked(MouseEvent m)
			{
				if (m.getClickCount() == 2 && leafNode)
				{
					if (rootNode)
						JOptionPane.showOptionDialog(getContentPane(), translations[21], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
					else
						// Version 1.6
						viewDocument(bookPath, "1");
				}
			}

			public void mouseReleased(MouseEvent m)
			{
				//if((m.isPopupTrigger() && isWindows)/* For Windows */ || ( (m.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK && !isWindows) /* For Linux */ )
				if ((m.getModifiers() & InputEvent.BUTTON3_MASK) == InputEvent.BUTTON3_MASK) // Version 1.7
				{
					final JTree displayedTree = authorTreeSelected ? authorTree : tree;

					final TreePath path = displayedTree.getPathForLocation(m.getX(), m.getY());
					final TreePath[] paths = displayedTree.getSelectionPaths();

					// Version 1.5, To allow multiple selection with popup menu
					boolean multipleSelection = false;
					if (paths != null)
					{
						for (TreePath element : paths)
						{
							if (element.equals(path))
							{
								multipleSelection = true;
								break;
							}
						}
					}
					if (!multipleSelection) displayedTree.setSelectionPath(path);

					/*
					// This function is used to select the selected path (using popuptrigger) with the previous selected paths.
					final TreePath paths[] = tree.getSelectionPaths();
					final TreePath treePaths[] = new TreePath[1+(paths==null?0:paths.length)];
					if(paths!=null) System.arraycopy(paths, 0, treePaths, 0, paths.length);
					treePaths[treePaths.length-1] = path;
					tree.setSelectionPaths(treePaths);
					*/

					// This is used to check if the mouse on one tree items
					if (path != null)
					{
						deleteMenuItem.setVisible(true);
						addMenuItem.setVisible(true);
						editMenuItem.setVisible(true);
						indexingMenu.setVisible(true);
						exportMenuItem.setVisible(true);

						// Version 1.5
						if (rootNode)
						{
							editMenuItem.setVisible(false);
							deleteMenuItem.setVisible(false);

							if (leafNode)
							{
								indexingMenu.setVisible(false);
								exportMenuItem.setVisible(false);
							}
						}
						else if (leafNode)
							addMenuItem.setVisible(false);

						if (displayedTree.getSelectionPaths().length > 1)
						{
							addMenuItem.setVisible(false);
							editMenuItem.setVisible(false);
						}

						if (language == lang.English)
							treePopupMenu.show(displayedTree, m.getX(), m.getY());
						else
						{
							treePopupMenu.updateUI(); // Version 1.6, for getPreferredSize() to return correct value
							treePopupMenu.show(displayedTree, m.getX() - treePopupMenu.getPreferredSize().width + 15, m.getY());
						}
					}
				}
			}
		};
		tree.addMouseListener(mouseAdapter);
		authorTree.addMouseListener(mouseAdapter);

		final JTextField listSearchTextField = new JTextField();
		final JButton listSearchButton = new JButton(new ImageIcon(programFolder + "images/search.png"));
		listSearchButton.setToolTipText(translations[107]);
		final ActionListener listSearchActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (!listSearchTextField.getText().trim().isEmpty())
				{
					final JTree displayedTree = authorTreeSelected ? authorTree : tree; // Version 1.7
					final TreeNode root = (TreeNode) displayedTree.getModel().getRoot();

					// Traverse tree from root
					expandAll(new TreePath(root));

					int startRow = 0;
					if (displayedTree.getMaxSelectionRow() != -1)
					{
						startRow = displayedTree.getMaxSelectionRow() + 1;
						if (startRow == displayedTree.getRowCount())
							startRow = 0;
					}

					TreePath path;
					boolean found = false;
					for (int i = startRow; i < displayedTree.getRowCount(); i++)
					{
						path = displayedTree.getPathForRow(i);
						//path = displayedTree.getNextMatch("", i, javax.swing.text.Position.Bias.Forward);
						if (path != null)
						{
							DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

							//if(node.isLeaf()) // Version 1.5, To allow searching the names of multi-volume books
							{
								if (node.toString().contains(listSearchTextField.getText().trim()))
								{
									//displayedTree.setLeadSelectionPath(path);
									displayedTree.setSelectionPath(path);
									displayedTree.scrollRectToVisible(new Rectangle(1000, 0, 0, 0)); // Version 1.7
									displayedTree.scrollPathToVisible(path);
									found = true;
									break;
								}
							}
						}
					}

					if (!found)
					{
						for (int i = 0; i < startRow; i++)
						{
							path = displayedTree.getNextMatch("", i, javax.swing.text.Position.Bias.Forward);
							if (path != null)
							{
								DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

								if (node.isLeaf())
								{
									if (node.toString().contains(listSearchTextField.getText()))
									{
										//displayedTree.setLeadSelectionPath(path);
										displayedTree.setSelectionPath(path);
										displayedTree.scrollRectToVisible(new Rectangle(1000, 0, 0, 0));  // Version 1.7
										displayedTree.scrollPathToVisible(path);
										break;
									}
								}
							}
						}
					}
				}
				else
					JOptionPane.showOptionDialog(getContentPane(), translations[37], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
			}

			// Expands all nodes in the tree. Try to find a way to speed the expansion.
			public void expandAll(final TreePath path)
			{
				final Object node = path.getLastPathComponent();
				TreeModel model = (authorTreeSelected ? authorTree : tree).getModel();
				if (model.isLeaf(node)) return;
				(authorTreeSelected ? authorTree : tree).expandPath(path);
				final int num = model.getChildCount(node);
				for (int i = 0; i < num; i++)
					expandAll(path.pathByAddingChild(model.getChild(node, i)));
			}

			/*
		    private void expandAll(TreePath parent)
		    {
		        // Traverse children
		        TreeNode node = (TreeNode)parent.getLastPathComponent();
		        if(node.getChildCount() >= 0)
		        {
		            for(Enumeration e=node.children(); e.hasMoreElements();)
		            {
		                TreeNode n = (TreeNode)e.nextElement();
		                TreePath path = parent.pathByAddingChild(n);
		                expandAll(path);
		            }
		        }

		        // Expansion or collapse must be done bottom-up
		        tree.expandPath(parent);
		    }
		    */
		};
		listSearchTextField.addActionListener(listSearchActionListener);
		listSearchButton.addActionListener(listSearchActionListener);

		// Version 1.4, History of the search inputs
		final JList<String> historyList = new JList<>();
		historyList.setModel(displayedHistoryListModel);

		try
		{
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(programFolder + "setting/history.txt"), StandardCharsets.UTF_8));
			while (in.ready()) historyListModel.addElement(in.readLine());
			in.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		// Initially
		for (int i = 0; i < historyListModel.size(); i++)
			displayedHistoryListModel.addElement(historyListModel.elementAt(i));

		final JPanel searchTextFieldPanel = new JPanel(new BorderLayout());
		final JTextField searchTextField = new JTextField();
		searchTextFieldPanel.add(searchTextField);

		final JPopupMenu historyPopup = new JPopupMenu();
		final ListSelectionModel historySelectionModel = historyList.getSelectionModel();
		historySelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historySelectionModel.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e)
			{
				int i = ((ListSelectionModel) e.getSource()).getMaxSelectionIndex();
				if (i != -1) searchTextField.setText(displayedHistoryListModel.getElementAt(i));
			}
		});

		historyList.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				historyPopup.setVisible(false);
			}
		});
		historyList.addKeyListener(new KeyListener()
		{
			public void keyTyped(KeyEvent e)
			{
			}

			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER) historyPopup.setVisible(false);
				if (e.getKeyCode() == KeyEvent.VK_DELETE)
				{
					int index = historyList.getSelectedIndex();
					if (index != -1)
					{
						historyListModel.removeElement(displayedHistoryListModel.elementAt(index));
						displayedHistoryListModel.removeElementAt(index);
						if (!displayedHistoryListModel.isEmpty())
						{
							if (displayedHistoryListModel.getSize() != index)
								historyList.setSelectedIndex(index);
							else
								historyList.setSelectedIndex(index - 1);
						}
						else
							historyPopup.setVisible(false);
					}
				}
			}

			public void keyReleased(KeyEvent e)
			{
			}
		});

		searchTextField.addKeyListener(new KeyListener()
		{
			public void keyTyped(KeyEvent e)
			{
			}

			public void keyPressed(KeyEvent e)
			{
			}

			public void keyReleased(KeyEvent e)
			{
				// Auto Complete
				displayedHistoryListModel.clear();
				for (int i = 0; i < historyListModel.size(); i++)
				{
					if ((historyListModel.elementAt(i)).startsWith(searchTextField.getText()))
						displayedHistoryListModel.addElement(historyListModel.elementAt(i));
				}

				if (!displayedHistoryListModel.isEmpty() && e.getKeyCode() != KeyEvent.VK_ENTER && e.getKeyCode() != KeyEvent.VK_ESCAPE)
					historyPopup.show(searchTextField, 1, 27);
				else
					historyPopup.setVisible(false);

				if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)
				{
					historyList.requestFocus();
					historyList.setSelectedIndex(0);
				}
				else
					searchTextField.requestFocus();
			}
		});

		final JDialog searchTreeDialog = new JDialog(this, translations[76], false);
		final DefaultTreeCellRenderer render = new DefaultTreeCellRenderer();
		render.setLeafIcon(null);
		render.setOpenIcon(null);
		render.setClosedIcon(null);
		searchTree.setCellRenderer(render);
		//searchTree.getCheckBoxTreeSelectionModel().setSelectionPaths(new TreePath[]{new TreePath(searchTree.getModel().getRoot())}); // To select all the nodes by default
		//searchTree.setChecked(searchTree.getRootNode(), true); // To select all the nodes by default
		searchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously

		authorSearchTree.setCellRenderer(render);
		//authorSearchTree.getCheckBoxTreeSelectionModel().setSelectionPaths(new TreePath[]{new TreePath(authorSearchTree.getModel().getRoot())}); // To select all the nodes by default
		//authorSearchTree.setChecked(authorSearchTree.getRootNode(), true); // To select all the nodes by default
		authorSearchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously

		final JTabbedPane searchTreeTabbedPane = new JTabbedPane();
		searchTreeTabbedPane.addTab(translations[57], new JScrollPane(searchTree));
		searchTreeTabbedPane.addTab(translations[58], new JScrollPane(authorSearchTree));

		searchTreeDialog.setContentPane(searchTreeTabbedPane);
		searchTreeDialog.setSize(260, 600);
		if (language != lang.English) searchTreeDialog.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		centerInScreen(searchTreeDialog);

		// Version 1.5
		final JButton searchRangeButton = new JButton(new ImageIcon(programFolder + "images/search_range.png"));
		searchRangeButton.setToolTipText(translations[121]);
		searchRangeButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				searchTreeDialog.setVisible(true);
			}
		});

		// This is to indicate the type of the index search
		final JCheckBoxMenuItem defaultSearchTypeButton = new JCheckBoxMenuItem(translations[128], true);
		final JCheckBoxMenuItem arabicRootsSearchTypeButton = new JCheckBoxMenuItem(translations[129]);
		final JCheckBoxMenuItem arabicLuceneSearchTypeButton = new JCheckBoxMenuItem(translations[130]);

		if (language == lang.English)
		{
			arabicRootsSearchTypeButton.setEnabled(false);
			arabicLuceneSearchTypeButton.setEnabled(false);
		}
		else
		{
			// Version 1.8
			if (language == lang.Urdu)
				arabicRootsSearchTypeButton.setEnabled(false);
		}

		final ButtonGroup searchGroup = new ButtonGroup();
		searchGroup.add(defaultSearchTypeButton);
		searchGroup.add(arabicRootsSearchTypeButton);
		searchGroup.add(arabicLuceneSearchTypeButton);

		final WebPopupMenu searchOptionsPopupMenu = new WebPopupMenu();
		searchOptionsPopupMenu.add(defaultSearchTypeButton);
		searchOptionsPopupMenu.add(arabicRootsSearchTypeButton);
		searchOptionsPopupMenu.add(arabicLuceneSearchTypeButton);
		if (language != lang.English)
			searchOptionsPopupMenu.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

		final JButton searchOptionsButton = new JButton(new ImageIcon(programFolder + "images/preferences.png"));
		searchOptionsButton.setToolTipText(translations[122]);
		searchOptionsButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				/*final Component c = (Component)e.getSource();
                searchOptionsPopupMenu.updateUI(); // Version 1.6, for getPreferredSize() to return correct value
				if(language!=lang.English)
					searchOptionsPopupMenu.show(c, c.getWidth()-searchOptionsPopupMenu.getPreferredSize().width, c.getHeight());
				else
					searchOptionsPopupMenu.show(c, 0, c.getHeight());*/
				searchOptionsPopupMenu.showBelow(searchOptionsButton);
			}
		});

		final JPanel searchOptionsPanel = new JPanel(new GridLayout(1, 2));
		searchOptionsPanel.add(searchOptionsButton);
		searchOptionsPanel.add(searchRangeButton);

		orderButton = new JButton(new ImageIcon(programFolder + "images/open.png"));
		orderButton.setToolTipText(translations[97]);
		orderButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				treeModel.reload();
				authorTreeModel.reload();

				// Disable listener (not needed here) to avoid the exception while deleting a node:
				//https://github.com/mgarin/weblaf/issues/633
				searchTree.removeCheckStateChangeListener(searchTreeChangeListener);
				authorSearchTree.removeCheckStateChangeListener(authorSearchTreeChangeListener);

				((DefaultTreeModel) searchTree.getModel()).reload();
				((DefaultTreeModel) authorSearchTree.getModel()).reload();

				//searchTree.getCheckBoxTreeSelectionModel().setSelectionPaths(new TreePath[]{new TreePath(searchTree.getModel().getRoot())}); // To select all the nodes by default
				//searchTree.setChecked(searchTree.getRootNode(), true); // To select all the nodes by default
				searchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously

				//authorSearchTree.getCheckBoxTreeSelectionModel().setSelectionPaths(new TreePath[]{new TreePath(authorSearchTree.getModel().getRoot())}); // To select all the nodes by default
				//authorSearchTree.setChecked(authorSearchTree.getRootNode(), true); // To select all the nodes by default
				authorSearchTree.checkAll(); // version 2.1, this will check all regardless of the state of the root. previous command will not check all in case root was checked previously

				searchTree.addCheckStateChangeListener(searchTreeChangeListener);
				authorSearchTree.addCheckStateChangeListener(authorSearchTreeChangeListener);

				bookName = "";
				bookPath = "";
				bookId = 0;
				bookAbsolutePath = "";
				bookCategory = "";
				bookAuthor = "";
				addType = "";
				bookParentName = ""; // Version 1.7

				searchResultsList.setModel(new DefaultListModel<>());
				currentSearchText = ""; // Version 1.7
				currentSearchType = defaultSearchTypeButton.isSelected() ? searchType.DEFAULT : (arabicRootsSearchTypeButton.isSelected() ? searchType.ROOTS : searchType.LUCENE); // Version 1.7
				searchResultsPathVector.removeAllElements();
				searchResultsPageVector.removeAllElements();
				searchResultsIdVector.removeAllElements();
				searchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[108], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));

				// Version 1.6
				enableEditPanel(false, translations[70]);
			}
		});

		final JPanel listControlPanel = new JPanel(new GridLayout(1, 5));
		listControlPanel.add(orderButton);
		listControlPanel.add(addButton);
		listControlPanel.add(editButton);
		listControlPanel.add(deleteButton);
		listControlPanel.add(indexingButton);

		final JPanel listSearchPanel = new JPanel(new BorderLayout());
		final JPanel listSearchButtonsPanel = new JPanel(new GridLayout(1, 2));
		listSearchButtonsPanel.add(exportButton);
		listSearchButtonsPanel.add(listSearchButton);

		if (language != lang.English) listSearchPanel.add(listSearchButtonsPanel, BorderLayout.EAST);
		else listSearchPanel.add(listSearchButtonsPanel, BorderLayout.WEST);
		listSearchPanel.add(listSearchTextField, BorderLayout.CENTER);

		final JPanel listOptionsPanel = new JPanel(new GridLayout(2, 1));
		listOptionsPanel.add(listSearchPanel);
		listOptionsPanel.add(listControlPanel);
		listPanel.add(listOptionsPanel, BorderLayout.SOUTH);
		//listPanel.setPreferredSize(listPanel.getPreferredSize()); // Version 1.5, To fix the width. Version 1.7, Removed after using JSplitPane

		final JButton searchButton = new JButton(translations[109], new ImageIcon(programFolder + "images/search.png"));
		final ActionListener SearchActionListener = new ActionListener()
		{
			// Version 1.4
			boolean stopSearch = false;

			public void actionPerformed(ActionEvent e)
			{
				stopSearch = false;

				// Version 1.4
				if (historyListModel.indexOf(searchTextField.getText()) == -1 && !searchTextField.getText().isEmpty())
					historyListModel.addElement(searchTextField.getText());

				historyPopup.setVisible(false);

				if (searchTextField.getText().trim().length() < 2)
					JOptionPane.showOptionDialog(getContentPane(), translations[45], translations[5], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[6]}, translations[6]);
				else
				{
					if (!searchThreadWork)
					{
						// Version 2.1, avoid 'BooleanQuery$TooManyClauses: maxClauseCount is set to 1024'
						BooleanQuery.setMaxClauseCount( Integer.MAX_VALUE );

						final Thread thread = new Thread()
						{
							public void run()
							{
								// This variable is used to stop searching two items at the same time.
								searchThreadWork = true;
								searchTextField.setEnabled(false);
								searchButton.setIcon(new ImageIcon(programFolder + "images/stop_search.png"));
								searchButton.setText(translations[73]);
								searchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[108], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));

								String searchText = searchTextField.getText().trim();
								currentSearchText = searchText; // Version 1.7, To Store the search text for use when highlighting the text in the displayed Area.
								currentSearchType = defaultSearchTypeButton.isSelected() ? searchType.DEFAULT : (arabicRootsSearchTypeButton.isSelected() ? searchType.ROOTS : searchType.LUCENE); // Version 1.7

								if (language == lang.Arabic)
								{
									// This is to ensure that '؟' (in arabic) is converted to '?' (in english)
									searchText = searchText.replace('؟', '?');

									// Version 1.1, replace all english search keywords to arabic
									// This is to ensure that " \u0648 " (arabic AND) is converted to " AND "
									searchText = searchText.replaceAll(" و ", " AND ");

									// This is to ensure that " \u0623\u0648 " (arabic OR) is converted to " OR "
									searchText = searchText.replaceAll(" أو ", " OR ");
								}
								else
								{
									if (language == lang.Urdu)
									{
										searchText = searchText.replace('؟', '?');
										searchText = searchText.replaceAll(" اور ", " AND ");
										searchText = searchText.replaceAll(" يا ", " OR ");
									}
								}

								long startTime = System.nanoTime();

								// Version 1.4
								searchResultsList.setModel(new DefaultListModel<>());

								//final DefaultListModel<String> searchResultsListModel = new DefaultListModel<>();
								final List<String> items = new ArrayList<>();

								searchResultsPathVector.removeAllElements();
								searchResultsPageVector.removeAllElements();
								searchResultsIdVector.removeAllElements();

								try
								{
									//Directory fsDir = FSDirectory.getDirectory(indexDir, false);
									// Version 1.4, search enhancement.
									final QueryParser queryParser = new QueryParser("pageContent", language != lang.English ? (defaultSearchTypeButton.isSelected() ? arabicAnalyzer : (arabicRootsSearchTypeButton.isSelected() ? arabicRootsAnalyzer : arabicLuceneAnalyzer)) : new StandardAnalyzer());

									queryParser.setAllowLeadingWildcard(true);
									queryParser.setDefaultOperator(QueryParser.Operator.AND);
									Query query = queryParser.parse(searchText);

									// The below prevent Highlighter from working, but it is needed in older lucene for '*' '?' in the searched word.
									// http://lucene.472066.n3.nabble.com/Highlighter-doesn-t-highlight-wildcard-queries-after-updating-to-2-9-1-3-0-0-td543308.html
									//query = query.rewrite((defaultSearchTypeButton.isSelected()?defaultSearcher:(arabicRootsSearchTypeButton.isSelected()?arabicRootsSearcher:arabicLuceneSearcher)).getIndexReader());

									// Version 1.5, Limiting the search
									//String filterQuery = "";
									final BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder(); // Version 1.8
									boolean fullSearch = true;

									// Version 1.5
									final java.util.List<DefaultMutableTreeNode> checkedNodes = searchTree.getNodes(CheckState.checked);
									if (!checkedNodes.isEmpty())
									{
										for (DefaultMutableTreeNode node : checkedNodes) // Version 1.7
										{
											final NodeInfo info = (NodeInfo) node.getUserObject();
											//if(path.getPathCount()==2) // i.e. category, there are many ways to do this using node.isLeaf/Root  ...
											if (info.path.equals("searchCategory")) // Version 1.8
											{
												fullSearch = false;
					    						/*if(!filterQuery.isEmpty())
													filterQuery = filterQuery + " OR category:\""+ info.name + '\"';
												else
													filterQuery = "category:\""+ info.name + '\"';*/
												booleanQueryBuilder.add(new BooleanClause(new TermQuery(new Term("category", info.name)), BooleanClause.Occur.SHOULD)); // Version 1.8
											}
											else
											{
												//if(path.getPathCount()==3) // i.e. leaf/book
												if (node.isLeaf() && !node.isRoot())
												{
													fullSearch = false;
													if (info.path.isEmpty())
													{
				    									/*if(!filterQuery.isEmpty())
															filterQuery = filterQuery + " OR (parent:\""+info.name+"\" AND category:\""+ info.category +"\")";
														else
															filterQuery = "(parent:\""+info.name+"\" AND category:\""+ info.category +"\")";*/
														final BooleanQuery.Builder innerBuilder = new BooleanQuery.Builder();
														innerBuilder.add(new TermQuery(new Term("parent", info.name)), BooleanClause.Occur.MUST);
														innerBuilder.add(new TermQuery(new Term("category", info.category)), BooleanClause.Occur.MUST);
														booleanQueryBuilder.add(innerBuilder.build(), BooleanClause.Occur.SHOULD);
													}
													else
													{
					    								/*if(!filterQuery.isEmpty())
															filterQuery = filterQuery + " OR path:\""+info.path.replaceAll("\\\\", "\\\\\\\\") + '\"';
														else
															filterQuery = "path:\""+ info.path.replaceAll("\\\\", "\\\\\\\\") + '\"';*/
														//booleanQueryBuilder.add(new TermQuery(new Term("path", info.path.replaceAll("\\\\", "\\\\\\\\"))), BooleanClause.Occur.SHOULD);
														//booleanQueryBuilder.add(new TermQuery(new Term("path", info.path)), BooleanClause.Occur.SHOULD);
														booleanQueryBuilder.add(new TermQuery(new Term("id", String.valueOf(info.id))), BooleanClause.Occur.SHOULD); // Version 2.1, TODO: test with normal file not in the pdf folder
													}
												}
											}
										}
									}

									final BooleanQuery booleanQuery = booleanQueryBuilder.build(); // Version 1.8

									if (/*!filterQuery.isEmpty()*/!booleanQuery.toString().isEmpty() || (fullSearch && !checkedNodes.isEmpty()))
									{
                                        /* Version 1.9, replaced by TopScoreDocCollector
                                        final org.apache.lucene.util.FixedBitSet bits = new org.apache.lucene.util.FixedBitSet((defaultSearchTypeButton.isSelected()?defaultSearcher:(arabicRootsSearchTypeButton.isSelected()?arabicRootsSearcher:arabicLuceneSearcher)).getIndexReader().maxDoc()); // Version 1.7
                                        final SimpleCollector collector = new SimpleCollector()
                                        {
                                            public int docBase;
                                            public void collect(int doc) {bits.set(doc + docBase);}
                                            public void doSetNextReader(LeafReaderContext context){this.docBase = context.docBase;}
                                            public boolean needsScores(){return false;};
                                        };
                                        */

										// Version 1.9
										final int hitsPerPage = 200;
										final TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, Integer.MAX_VALUE); // TODO: score the results
										final ScoreDoc[] hits;
										if (fullSearch)
										{
											final TopDocs results = (defaultSearchTypeButton.isSelected() ? defaultSearcher : (arabicRootsSearchTypeButton.isSelected() ? arabicRootsSearcher : arabicLuceneSearcher)).search(query, hitsPerPage); // Version 1.9
											hits = results.scoreDocs;
										}
										else
										{
											//final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
											final BooleanQuery.Builder finalBooleanQueryBuilder = new BooleanQuery.Builder();
											finalBooleanQueryBuilder.add(query, BooleanClause.Occur.FILTER);
											//finalBooleanQueryBuilder.add(new QueryWrapperFilter(booleanQuery), BooleanClause.Occur.FILTER); // This will help: http://stackoverflow.com/questions/4489033/lucene-filtering-for-documents-not-containing-a-term
											finalBooleanQueryBuilder.add(booleanQuery, BooleanClause.Occur.FILTER); // TODO recheck
											//System.out.println(finalBooleanQueryBuilder.build());
											//(defaultSearchTypeButton.isSelected()?defaultSearcher:(arabicRootsSearchTypeButton.isSelected()?arabicRootsSearcher:arabicLuceneSearcher)).search(query, new QueryWrapperFilter(filterQueryParser.parse(filterQuery)), collector);
											(defaultSearchTypeButton.isSelected() ? defaultSearcher : (arabicRootsSearchTypeButton.isSelected() ? arabicRootsSearcher : arabicLuceneSearcher)).search(finalBooleanQueryBuilder.build(), collector);
											hits = collector.topDocs().scoreDocs;
										}

										//final Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("{", ":c(255,0,0)}"), new QueryScorer(query));
										final Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<font color=maroon>", "</font>"), new QueryScorer(query));
										if (defaultSearchTypeButton.isSelected())
										{
											for (int j = 0, index = 0; j < hits.length && !stopSearch; j++)
											{
												final Document doc = defaultSearcher.doc(hits[j].doc);
												final String txt = doc.get("pageContent");
												String text = "";

												final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", defaultSearcher.getIndexReader().getTermVectors(hits[j].doc), txt, language != lang.English ? arabicAnalyzer : new StandardAnalyzer(), highlighter.getMaxDocCharsToAnalyze() - 1); // Version 1.9
												final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
												for (TextFragment f : frag)
													if ((f != null) && (f.getScore() > 0F))
														text = (text.isEmpty() ? "" : (text + " ... ")) + f.toString();

												items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " + text + "</HTML>"); // Version 1.6/1.7
												searchResultsPathVector.add(doc.get("path"));
												searchResultsPageVector.add(doc.get("page"));
												searchResultsIdVector.add(doc.get("id"));
											}
										}
										else
										{
											if (arabicRootsSearchTypeButton.isSelected())
											{
												for (int j = 0, index = 0; j < hits.length && !stopSearch; j++)
												{
													final Document doc = arabicRootsSearcher.doc(hits[j].doc);
													final String txt = doc.get("pageContent");
													String text = "";

													final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", arabicRootsSearcher.getIndexReader().getTermVectors(hits[j].doc), txt, arabicRootsAnalyzer, highlighter.getMaxDocCharsToAnalyze() - 1); // Version 1.9
													final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
													for (TextFragment tf : frag)
														if ((tf != null) && (tf.getScore() > 0))
															text = (text.isEmpty() ? "" : (text + " ... ")) + tf.toString();

													items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " + text + "</HTML>"); // Version 1.6/1.7
													searchResultsPathVector.add(doc.get("path"));
													searchResultsPageVector.add(doc.get("page"));
													searchResultsIdVector.add(doc.get("id"));
												}
											}
											else // i.e. arabicLuceneSearchTypeButton.isSelected()
											{
												for (int j = 0, index = 0; j < hits.length && !stopSearch; j++)
												{
													final Document doc = arabicLuceneSearcher.doc(hits[j].doc);
													final String txt = doc.get("pageContent");
													String text = "";

													final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", arabicLuceneSearcher.getIndexReader().getTermVectors(hits[j].doc), txt, arabicLuceneAnalyzer, highlighter.getMaxDocCharsToAnalyze() - 1); // Version 1.9
													final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
													for (TextFragment tf : frag)
														if ((tf != null) && (tf.getScore() > 0))
															text = (text.isEmpty() ? "" : (text + " ... ")) + tf.toString();

													items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " + text + "</HTML>"); // Version 1.6/1.7
													searchResultsPathVector.add(doc.get("path"));
													searchResultsPageVector.add(doc.get("page"));
													searchResultsIdVector.add(doc.get("id"));
												}
											}
										}

                                        /* Version 1.9, replaced by above
                                        if(defaultSearchTypeButton.isSelected())
										{
                                            for(int j=0, size=bits.length(), index=0; j<size && !stopSearch; j++)
											//for(int j=0; j<30; j++)
										    {
										    	if(bits.get(j))
										    	{
										    		//int id = hits.scoreDocs[j].doc;
										    		final Document doc = defaultSearcher.doc(j);
										    		//final Document doc = defaultSearcher.doc(id);
                                                    final String txt = doc.get("pageContent");
											    	String text = "";

											    	// Version 1.5
											    	//final TokenStream tokenStream = TokenSources.getAnyTokenStream(defaultSearcher.getIndexReader(), /*id/j, "pageContent", language!=lang.English?arabicAnalyzer:new StandardAnalyzer());
                                                    final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", defaultSearcher.getIndexReader().getTermVectors(j), txt, language!=lang.English?arabicAnalyzer:new StandardAnalyzer(), highlighter.getMaxDocCharsToAnalyze()-1); // Version 1.9
                                                    final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
												    for(TextFragment f : frag)
												    	if((f != null) && (f.getScore() > 0F))
												    	{
												   	 		text=(text.isEmpty()?"":(text+" ... "))+f.toString();
												    		//break;
												    	}

												    //text=highlighter.getBestFragments(tokenStream, text, 10, " .... ");

												    //searchResultsListModel.addElement("<HTML>"+(language!=lang.English?"<div align=right>":"")+"("+(++index)+")<font color=maroon>["+(doc.get("parent").isEmpty()?doc.get("name"):doc.get("parent"))+"]</font> "+/* Version 1.4 /text+"</HTML>"); // Version 1.6/1.7
                                                    items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " +/* Version 1.4 /text + "</HTML>"); // Version 1.6/1.7
                                                    //searchResultsListModel.addElement("("+(++index)+"){["+(doc.get("parent").isEmpty()?doc.get("name"):doc.get("parent"))+"]:c(128,0,0)} "+/* Version 1.4 /text); // Version 1.9
													//searchResultsListModel.addElement("("+count+")["+doc.get("category")+(doc.get("parent").equals("")?"":(", "+doc.get("parent")))+", "+doc.get("name")+", "+translations[64]+doc.get("page")+"] "+/* Version 1.4 /text);
											    	searchResultsPathVector.add(doc.get("path"));
											    	searchResultsPageVector.add(doc.get("page"));
										    	}
										    }
										}
										else // Version 1.5
										{
											if(arabicRootsSearchTypeButton.isSelected())
											{
                                                for(int j=0, size=bits.length(), index=0; j<size && !stopSearch; j++)
												{
                                                    if(bits.get(j))
											    	{
                                                        final Document doc = arabicRootsSearcher.doc(j);
											    		final String txt = doc.get("pageContent");
                                                        String text = "";

                                                        //final TokenStream tokenStream = TokenSources.getAnyTokenStream(arabicRootsSearcher.getIndexReader(), j, "pageContent", arabicRootsAnalyzer);
                                                        final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", arabicRootsSearcher.getIndexReader().getTermVectors(j), txt, arabicRootsAnalyzer, highlighter.getMaxDocCharsToAnalyze()-1); // Version 1.9
												    	final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
													    for(TextFragment tf : frag)
													    	if((tf != null) && (tf.getScore() > 0))
													   	 		text=(text.isEmpty()?"":(text+" ... "))+tf.toString();

														//searchResultsListModel.addElement("<HTML>"+(language!=lang.English?"<div align=right>":"")+"("+(++index)+")<font color=maroon>["+(doc.get("parent").isEmpty()?doc.get("name"):doc.get("parent"))+"]</font> "+text+"</HTML>"); // Version 1.6/1.7
                                                        items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " + text + "</HTML>"); // Version 1.6/1.7
														searchResultsPathVector.add(doc.get("path"));
												    	searchResultsPageVector.add(doc.get("page"));
											    	}
											    }
											}
											else // i.e. arabicLuceneSearchTypeButton.isSelected()
											{
												for(int j=0, size=bits.length(), index=0; j<size && !stopSearch; j++)
												{
											    	if(bits.get(j))
											    	{
											    		final Document doc = arabicLuceneSearcher.doc(j);
                                                        final String txt = doc.get("pageContent");
											    		String text="";

												    	//final TokenStream tokenStream = TokenSources.getAnyTokenStream(arabicLuceneSearcher.getIndexReader(), j, "pageContent", arabicLuceneAnalyzer);
                                                        final TokenStream tokenStream = TokenSources.getTokenStream("pageContent", arabicLuceneSearcher.getIndexReader().getTermVectors(j), txt, arabicLuceneAnalyzer, /*-1/highlighter.getMaxDocCharsToAnalyze()-1); // Version 1.9
                                                        final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 1);
													    for(TextFragment tf : frag)
													    	if((tf != null) && (tf.getScore() > 0))
													   	 		text=(text.isEmpty()?"":(text+" ... "))+tf.toString();

														//searchResultsListModel.addElement("<HTML>"+(language!=lang.English?"<div align=right>":"")+"("+(++index)+")<font color=maroon>["+(doc.get("parent").isEmpty()?doc.get("name"):doc.get("parent"))+"]</font> "+text+"</HTML>"); // Version 1.6/1.7
                                                        items.add("<HTML>" + (language != lang.English ? "<div align=right>" : "") + "(" + (++index) + ")<font color=maroon>[" + (doc.get("parent").isEmpty() ? doc.get("name") : doc.get("parent")) + "]</font> " + text + "</HTML>"); // Version 1.6/1.7
														searchResultsPathVector.add(doc.get("path"));
												    	searchResultsPageVector.add(doc.get("page"));
											    	}
											    }
											}
										}
										*/

										// Version 1.2, Displaying the results number.
										searchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[110] + items.size() + ']', TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));

										SwingUtilities.invokeLater(new Runnable()
										{
											public void run()
											{
												//searchResultsList.setModel(searchResultsListModel);
												searchResultsList.setModel(new FastListModel(items)); // TODO: no improvement
												searchResultsList.scrollRectToVisible(new Rectangle(8000, 0, 0, 0)); // Version 1.5, will not work unless inside SwingUtilities.invokeLater
											}
										});
									}
									else
										JOptionPane.showOptionDialog(getContentPane(), translations[123], translations[3], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[44]}, translations[44]);
								}
								catch (OutOfMemoryError e)
								{
									// Free the memory
									searchResultsPathVector.removeAllElements();
									searchResultsPageVector.removeAllElements();
									searchResultsIdVector.removeAllElements();
									SwingUtilities.invokeLater(new Runnable()
									{
										public void run()
										{
											searchResultsList.setModel(new DefaultListModel<>());

											JOptionPane.showOptionDialog(getContentPane(), translations[124] +
													System.lineSeparator() + translations[125] + System.lineSeparator() +
													translations[126] + System.lineSeparator() + translations[127], translations[3], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[44]}, translations[44]);
											searchPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[108], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
										}
									});
								}
								catch (Exception e)
								{
									e.printStackTrace();
								}

								System.out.println("Time: " + ((System.nanoTime() - startTime) / 1000000000.0));

								searchThreadWork = false;
								searchTextField.setEnabled(true);
								searchButton.setIcon(new ImageIcon(programFolder + "images/search.png"));
								searchButton.setText(translations[109]);
							}
						};
						thread.start();
					}
					// Version 1.4
					else
						stopSearch = true;
				}
			}
		};
		searchButton.addActionListener(SearchActionListener);
		searchTextField.addActionListener(SearchActionListener);

		if (language == lang.English)
		{
			searchTextFieldPanel.add(searchButton, BorderLayout.EAST);
			searchTextFieldPanel.add(searchOptionsPanel, BorderLayout.WEST);
		}
		else
		{
			searchTextFieldPanel.add(searchButton, BorderLayout.WEST);
			searchTextFieldPanel.add(searchOptionsPanel, BorderLayout.EAST);
		}

		searchResultsList = new JList<>();
		searchResultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		// The cause of multi-line width in the list is because of its length. <HTML> tag will try to fit the length in many lines to wrap the text. <div align=right> to make it in the right in case multi line
		//searchResultsList.setPrototypeCellValue("\u0627\u0644\u062D\u0645\u062F\u064F \"(\u064F\u0644\u0644\u0647\u0650 \u0627\u0644\u0642\u064E\u062F\u064A\u0645\u0650 \u0627\u0644\u0628\u0627\u0642\u064A [1] [ \u0645\u064F\u0633\u064E\u0628\u0651\u0650\u0628\u0650 \u0627\u0644\u0623\u064E\u0633\u0628\u0627\u0628\u0650 ] \u0648\u0627\u0644\u0623\u0631\u0632\u0627\u0642\u0650\u062D\u064E\u064A\u0651\u064C \u0639\u064E\u0644\u064A\u0645\u064C \u0642\u0627\u062F\u0650\u0631\u064C \u0645\u064E\u0648\u062C\u0648\u062F\u064F [2] \u0642\u0627\u0645\u064E\u062A\u0652 \u0628\u0647\u0650 \u0627\u0644\u0623\u0634\u064A\u0627\u0621\u064F \u0648\u0627\u0644\u0648\u062C\u0648\u062F\u064F\u062F\u064E\u0644\u0651\u064E\u062A \u0639\u0644\u0649 \u0648\u062C\u0648\u062F\u0650\u0647\u0650 \u0627\u0644\u0652\u062D\u064E\u0648\u0627\u062F\u0650\u062B\u064F [3] \u0633\u064F\u0628\u062D\u0627\u0646\u064F\u0647\u064F \u0641\u064E\u0647\u0652\u0648\u064E \u0627\u0644\u062D\u0643\u064A\u0645\u064F \u0627\u0644\u0648\u0627\u0631\u0650\u062B\u064F\u062B\u064F\u0645\u0651\u064E \u0627\u0644\u0635\u0651\u064E\u0644\u0627\u0629\u064F \u0648\u0627\u0644\u0633\u0651\u064E\u0644\u0627\u0645\u064F \u0633\u064E\u0631\u0645\u064E\u062F\u064E\u0627\u0652 [4] \u0639\u0644\u0649 \u0627\u0644\u0646\u0651\u064E\u0628\u0650\u064A\u0651\u0650 \u0627\u0644\u0652\u0645\u064F\u0635\u0637\u064E\u0641\u064E\u0649 \u0643\u064E\u0646\u0652\u0632\u0650 \u0627\u0644\u0652\u0647\u064F\u062F\u064E\u0649\u0648\u064E\u0622\u0644\u0650\u0647\u0650 \u0648\u064E\u0635\u064E\u062D\u0628\u0650\u0647\u0650 \u0627\u0644\u0623\u0628\u0631\u0627\u0631\u0650 [5] \u0645\u064E\u0639\u0627\u062F\u0650\u0646\u0650");
		//final ListSelectionModel searchListSelectionModel = searchResultsList.getSelectionModel();

		final Pattern CRLF = Pattern.compile("(\r\n|\n)");

		// Version 1.6
		// List Selection Listener for Search panel.
		searchResultsList.addListSelectionListener(new ListSelectionListener() // Version 1.6
		{
			public void valueChanged(ListSelectionEvent e)
			{
				if (!e.getValueIsAdjusting()) // Version 1.5, To fire it only once when clicking by mouse.
				{
					//ListSelectionModel lsm = (ListSelectionModel)e.getSource();
					//searchResultSelectedIndex = lsm.getMaxSelectionIndex();

					searchResultSelectedIndex = searchResultsList.getSelectedIndex(); // Version 1.6
					if (searchResultSelectedIndex != -1)
					{
						TreePath treePath = null;
						DefaultMutableTreeNode node;
						final Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) (authorTreeSelected ? authorTree : tree).getModel().getRoot()).postorderEnumeration();
						while (nodes.hasMoreElements())
						{
							node = (DefaultMutableTreeNode) nodes.nextElement();
							if (node.isLeaf())
								//if (((NodeInfo) (node.getUserObject())).path.equals(searchResultsPathVector.elementAt(searchResultSelectedIndex)))
								if (((NodeInfo) (node.getUserObject())).id == Integer.parseInt(searchResultsIdVector.elementAt(searchResultSelectedIndex))) // Version 2.1
								{
									treePath = new TreePath(node.getPath());
									break;
								}
						}

						fireTreeSelectionListener.set(false); // To not fire the below SQL code again in treeSelectionListener
						if (authorTreeSelected) // Version 1.7
						{
							authorTree.setSelectionPath(treePath);
							authorTree.scrollRectToVisible(new Rectangle(1000, 0, 0, 0)); // Version 1.7
							authorTree.scrollPathToVisible(treePath);
						}
						else
						{
							tree.setSelectionPath(treePath);
							tree.scrollRectToVisible(new Rectangle(1000, 0, 0, 0)); // Version 1.7
							tree.scrollPathToVisible(treePath);
						}
						fireTreeSelectionListener.set(true);

						// Version 1.6
						try
						{
							// You can get the content from the index itself as well ..... doc.get("content") as in Quran. The performance of the below is still good (~0.005 sec)
							final Statement stmt = sharedDBConnection.createStatement();
							ResultSet rs = stmt.executeQuery("SELECT content FROM b" + searchResultsIdVector.elementAt(searchResultSelectedIndex) + " WHERE page = " + searchResultsPageVector.elementAt(searchResultSelectedIndex));
							if (rs.next()) // It should be there since it is indexed/searched because it is available
							{
								// Version 1.7, Note that the user can change the search Type (press another type button) while browsing the result of the search of that type. searchType enum is used to store the search type since the user can change it.
								//final QueryParser parser = new QueryParser(Version.LUCENE_36, "pageContent", language?(defaultSearchTypeButton.isSelected()?arabicAnalyzer:(arabicRootsSearchTypeButton.isSelected()?arabicRootsAnalyzer:arabicLuceneAnalyzer)):new StandardAnalyzer(Version.LUCENE_36));
								final QueryParser parser = new QueryParser("pageContent", language != lang.English ? ((currentSearchType == searchType.DEFAULT) ? arabicAnalyzer : ((currentSearchType == searchType.ROOTS) ? arabicRootsAnalyzer : arabicLuceneAnalyzer)) : new StandardAnalyzer());

								// Version 1.7
								final Reader description = rs.getCharacterStream("content");
								final char[] arr = new char[2 * 1024]; // 2K at a time
								final StringBuilder buf = new StringBuilder();
								int numChars;

								while ((numChars = description.read(arr, 0, arr.length)) > 0)
									buf.append(arr, 0, numChars);

								final String page = buf.toString(); // Version 1.7
								final Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<font color=red>", "</font>"), new QueryScorer(parser.parse(currentSearchText))); // Version 1.7, currentSearchText instead of searchTextField.getText()
								highlighter.setTextFragmenter(new NullFragmenter());
								final String text = highlighter.getBestFragment(language != lang.English ? ((currentSearchType == searchType.DEFAULT) ? arabicAnalyzer : ((currentSearchType == searchType.ROOTS) ? arabicRootsAnalyzer : arabicLuceneAnalyzer)) : new StandardAnalyzer(), "", page); // Version 1.7, searchType enum

								if (text != null)
								{
									final Matcher m = CRLF.matcher(text);
									setText(m.replaceAll("<br>"), "text/html", programFolder + "images/edit.png", translations[103]);
								}
								else
									setText(page, "text/plain", programFolder + "images/save.png", translations[70]);

								displayEditTextPane.setCaretPosition(0);
							}
							else
								setText("", "text/plain", programFolder + "images/save.png", translations[70]);

							pageTextField.setText(searchResultsPageVector.elementAt(searchResultSelectedIndex));
							currentDisplayedPage = searchResultsPageVector.elementAt(searchResultSelectedIndex);
							rs = stmt.executeQuery("SELECT MAX(Page) FROM b" + searchResultsIdVector.elementAt(searchResultSelectedIndex));
							rs.next();
							pagesTextField.setText(rs.getString(1));

							stmt.close();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
						fireSearchListSelectionListener = true; // Version 1.6
					}
				}
			}
		});

		// Version 1.5, MouseAdapter() instead of MouseListener() since you don't need to override all the methods.
		searchResultsList.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent m)
			{
				// Version 1.6
				if (!fireSearchListSelectionListener)
				{
					int temp = searchResultSelectedIndex;
					searchResultsList.clearSelection();
					searchResultsList.setSelectedIndex(temp);
				}

				// searchResultSelectedIndex!=-1 is added to not be activated when nothing is displayed.
				if (m.getClickCount() == 2 && searchResultSelectedIndex != -1)
					// Version 1.6
					viewDocument(searchResultsPathVector.elementAt(searchResultSelectedIndex), searchResultsPageVector.elementAt(searchResultSelectedIndex));
			}
		});

		final JPanel resultsPanel = new JPanel(new BorderLayout());
		final JScrollPane searchScrollPane = new JScrollPane(searchResultsList); //, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
		//searchScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE); // This seems to not improve the performance.
		//searchScrollPane.getVerticalScrollBar().setUnitIncrement(10);
		resultsPanel.add(searchScrollPane);

		searchPanel.add(searchTextFieldPanel, BorderLayout.NORTH);
		searchPanel.add(resultsPanel, BorderLayout.CENTER);

		// Version 1.6, Display/Edit panel
		final JPanel displayEditPanel = new JPanel(new BorderLayout());
		displayEditPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), translations[56], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));

		// Version 1.6
		displayEditTextPane = new JTextPane();
        /*
        {
            public void updateUI()
            {
                super.updateUI();
                //if(getBackground()==null || getBackground() instanceof javax.swing.plaf.UIResource)
                    setBackground(new Color(254,251,231));
            }
        };*/
		/*
        {
            public void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        */

		// Version 1.7
		final UndoManager undo = new UndoManager();
		javax.swing.text.Document doc = displayEditTextPane.getDocument();

		// Listen for undo and redo events
		doc.addUndoableEditListener(new UndoableEditListener()
		{
			public void undoableEditHappened(UndoableEditEvent evt)
			{
				undo.addEdit(evt.getEdit());
			}
		});

		// Create an undo action and add it to the text component
		displayEditTextPane.getActionMap().put("Undo", new AbstractAction("Undo")
		{
			public void actionPerformed(ActionEvent evt)
			{
				try
				{
					if (undo.canUndo())
						undo.undo();
				}
				catch (CannotUndoException e)
				{
					e.printStackTrace();
				}
			}
		});

		// Bind the undo action to ctl-Z
		displayEditTextPane.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "Undo");

		// Create a redo action and add it to the text component
		displayEditTextPane.getActionMap().put("Redo", new AbstractAction("Redo")
		{
			public void actionPerformed(ActionEvent evt)
			{
				try
				{
					if (undo.canRedo())
						undo.redo();
				}
				catch (CannotRedoException e)
				{
					e.printStackTrace();
				}
			}
		});

		// Bind the redo action to ctl-Y
		displayEditTextPane.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "Redo");

		pageTextField = new JTextField(5);
		pagesTextField = new JTextField(5);

		// Version 1.6, To register the font for the application to be be available in all context e.g. <HTML> tag ... etc
		// This resolve the issue of the font not working in Linux/Unix when using <HTML> tag
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

		// uthman.otf does not have English letters. you need to mix it yourself if you want to display both in the same book. But JTextPane is handling this by itself, JTextArea is not.
		// Version 2.0 changed to Amiri-Regular.otf which works for both latin and arabic. In addition, uthman.otf does not work properly with many names like لفظ الجلالة and causes issues with Text wrapping in JScrollPane
		if (language == lang.Arabic)
		{
			try
			{
				ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(programFolder + "bin/Amiri-Regular.otf")));

				// Version 1.7, To setFont even in "text/html"
				displayEditTextPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
				displayEditTextPane.setFont(new Font("Amiri", Font.PLAIN, 24));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			// Version 1.8
			if (language == lang.Urdu)
			{
				try
				{
					ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File(programFolder + "bin/JameelNooriNastaleeq.ttf")));

					// To setFont even in "text/html"
					displayEditTextPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
					displayEditTextPane.setFont(new Font("Jameel Noori Nastaleeq", Font.PLAIN, 24));
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}

		//displayEditTextPane.setContentType("text/html");
		//displayEditTextPane.setLineWrap(true);
		displayEditTextPane.setBackground(new javax.swing.plaf.ColorUIResource(254, 251, 231)); // Version 1.7, ColorUIResource or it will not work !

		displayEditPanel.add(new JScrollPane(displayEditTextPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
		displayEditTextPane.addKeyListener(new KeyListener()
		{
			public void keyTyped(KeyEvent e)
			{
			}

			public void keyPressed(KeyEvent e)
			{
				// Ctrl+S
				final boolean isAccelerated = (e.getModifiers() & java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
				if (e.getKeyCode() == KeyEvent.VK_S && isAccelerated && saveButton.isEnabled())
					saveButton.doClick();

				// Version 1.7
				if (e.getKeyCode() == (language != lang.English ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT) && isAccelerated && forwardButton.isEnabled())
					forwardButton.doClick();

				if (e.getKeyCode() == (language != lang.English ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT) && isAccelerated && backButton.isEnabled())
					backButton.doClick();
			}

			public void keyReleased(KeyEvent e)
			{
			}
		});

		//final Pattern CRLF = Pattern.compile("(\r\n|\n)");

		forwardButton = new JButton(new ImageIcon(programFolder + (language != lang.English ? "images/forward.png" : "images/backward.png")));
		forwardButton.setToolTipText(translations[77]);
		forwardButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (!pageTextField.getText().equals(pagesTextField.getText()) && !pageTextField.getText().isEmpty())
				{
					fireSearchListSelectionListener = false; // Version 1.7
					int currentPage = Integer.parseInt(pageTextField.getText());
					try
					{
						final Statement stmt = sharedDBConnection.createStatement();
						final ResultSet rs = stmt.executeQuery("SELECT content FROM b" + bookId + " WHERE page=" + (currentPage + 1));
						if (rs.next())
						{
							// Version 1.7
							final Reader description = rs.getCharacterStream("content");
							final char[] arr = new char[2 * 1024]; // 2K at a time
							final StringBuilder buf = new StringBuilder();
							int numChars;

							while ((numChars = description.read(arr, 0, arr.length)) > 0)
								buf.append(arr, 0, numChars);

							//final Matcher m = CRLF.matcher(rs.getString("content"));
							//displayEditTextPane.setText(m.replaceAll("<br>"));
							setText(buf.toString(), "text/plain", programFolder + "images/save.png", translations[70]); // Version 1.7
							//displayEditTextPane.getDocument().putProperty(javax.swing.text.DefaultEditorKit.EndOfLineStringProperty, "<br>");
							displayEditTextPane.setCaretPosition(0);
						}
						else
							setText("", "text/plain", programFolder + "images/save.png", translations[70]);

						pageTextField.setText(String.valueOf(currentPage + 1));
						currentDisplayedPage = pageTextField.getText();
						stmt.close();
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
			}
		});

		backButton = new JButton(new ImageIcon(programFolder + (language != lang.English ? "images/backward.png" : "images/forward.png")));
		backButton.setToolTipText(translations[78]);
		backButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (!pageTextField.getText().equals("1") && !pageTextField.getText().isEmpty())
				{
					fireSearchListSelectionListener = false; // Version 1.7
					int currentPage = Integer.parseInt(pageTextField.getText());
					try
					{
						final Statement stmt = sharedDBConnection.createStatement();
						final ResultSet rs = stmt.executeQuery("SELECT content FROM b" + bookId + " WHERE page=" + (currentPage - 1));
						if (rs.next())
						{
							// Version 1.7
							final Reader description = rs.getCharacterStream("content");
							final char[] arr = new char[2 * 1024];
							final StringBuilder buf = new StringBuilder();
							int numChars;

							while ((numChars = description.read(arr, 0, arr.length)) > 0)
								buf.append(arr, 0, numChars);

							setText(buf.toString(), "text/plain", programFolder + "images/save.png", translations[70]); // Version 1.7
							displayEditTextPane.setCaretPosition(0);
						}
						else
							setText("", "text/plain", programFolder + "images/save.png", translations[70]);

						pageTextField.setText(String.valueOf(currentPage - 1));
						currentDisplayedPage = pageTextField.getText();
						stmt.close();
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
			}
		});

		saveButton = new JButton(new ImageIcon(programFolder + "images/save.png"));
		saveButton.setToolTipText(translations[70]);
		saveButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (saveButton.getToolTipText().equals(translations[70]))
				{
					try
					{
                        /*
                        final Statement stmt = sharedDBConnection.createStatement();
                        if(stmt.executeUpdate("UPDATE \""+bookPath+"\" SET content='"+displayEditTextPane.getText()+"' WHERE page="+pageTextField.getText())!=1) // i.e. if not exist
                            stmt.executeUpdate("INSERT INTO \""+bookPath+"\" VALUES ("+pageTextField.getText()+",'"+displayEditTextPane.getText()+"')");
                        stmt.close();
                        */

						// Version 1.7, Get the page without HTML tags but with newlines as "\n". TODO: none of these are working. so we are changing the contentType from text/html <-> text/plain
						// displayEditTextPane.getDocument().getText(0, displayEditTextPane.getDocument().getLength()) is not working with newlines
                        /* Not working
                        final javax.swing.text.Element section = displayEditTextPane.getDocument().getDefaultRootElement();

                        // Get number of paragraphs.
                        int paraCount = section.getElementCount();

                        String page = "";

                        // Get index ranges for each paragraph
                        for (int i=0; i<paraCount; i++)
                        {
                            javax.swing.text.Element el = section.getElement(i);
                            int rangeStart = el.getStartOffset();
                            int rangeEnd = el.getEndOffset();
                            if(page.isEmpty())
                                page = displayEditTextPane.getText(rangeStart, rangeEnd-rangeStart);
                            else
                                page = page + lineSeparator + displayEditTextPane.getText(rangeStart, rangeEnd-rangeStart);
                        }
                        */

						// http://stackoverflow.com/questions/1859686/getting-raw-text-from-jtextpane
						// Not working also because Html2Test.handleText invoke each occurrence of white space (any newlines, tabs, carriage returns, or multiple spaces) is coalesced into a single space character. [http://java.sun.com/products/jfc/tsc/articles/bookmarks/]
						//Html2Text parser = new Html2Text();
						//parser.parse(new StringReader(displayEditTextPane.getText()));

						//final org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(displayEditTextPane.getText());
						//final org.jsoup.nodes.TextNode tn = new org.jsoup.nodes.TextNode(doc.body().html(), "");

						String page = displayEditTextPane.getText();
						//System.out.println(page);
						//System.out.println(displayEditTextPane.getDocument().getText(0, displayEditTextPane.getDocument().getLength()));

						//page = page.substring(page.indexOf("<body>")+10, page.indexOf("</body>"));
						//System.out.println(page);

						//page = page.replaceAll("<br>", "br2n");
						//page = org.jsoup.Jsoup.parse(page).text(); // change <br>\n to normal text e.g. br2n
						//System.out.println(page);
						//page = page.replaceAll("br2n", "\n"); // replace br2n to newline

						final PreparedStatement ps1 = sharedDBConnection.prepareStatement("UPDATE b" + bookId + " SET content=? WHERE page=" + pageTextField.getText());
						ps1.setString(1, page); //parser.getText()   OR Jsoup.parse(displayEditTextPane.getText()).text()

						if (ps1.executeUpdate() != 1) // i.e. if not exist
						{
							final PreparedStatement ps2 = sharedDBConnection.prepareStatement("INSERT INTO b" + bookId + " VALUES (" + pageTextField.getText() + ", ?)");
							ps2.setString(1, page);
							ps2.execute();
						}
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
				else
				{
					// Remove formatting. the same as pageTextField ActionListener
					try
					{
						final Statement stmt = sharedDBConnection.createStatement();
						final ResultSet rs = stmt.executeQuery("SELECT content FROM b" + bookId + " WHERE page=" + pageTextField.getText());
						if (rs.next())
						{
							// Version 1.7
							final Reader description = rs.getCharacterStream("content");
							final char[] arr = new char[2 * 1024];
							final StringBuilder buf = new StringBuilder();
							int numChars;

							while ((numChars = description.read(arr, 0, arr.length)) > 0)
								buf.append(arr, 0, numChars);

							setText(buf.toString(), "text/plain", programFolder + "images/save.png", translations[70]); // Version 1.7
							displayEditTextPane.setCaretPosition(0);
						}
						else
							setText("", "text/plain", programFolder + "images/save.png", translations[70]);
						stmt.close();
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
			}
		});

		viewButton = new JButton(new ImageIcon(programFolder + "images/PDF.png"));
		viewButton.setToolTipText(translations[72]);
		viewButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				viewDocument(bookPath, pageTextField.getText());
			}
		});

		pageTextField.addFocusListener(new FocusListener()
		{
			public void focusGained(FocusEvent e)
			{
			}

			public void focusLost(FocusEvent e)
			{
				pageTextField.setText(currentDisplayedPage);
			}
		});

		pageTextField.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (pageTextField.getText().isEmpty())
				{
					JOptionPane.showOptionDialog(getContentPane(), translations[64], translations[114], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[84]}, translations[84]);
					pageTextField.setText(currentDisplayedPage);
				}
				else
				{
					final int currentPage = Integer.parseInt(pageTextField.getText());
					final int maxPages = Integer.parseInt(pagesTextField.getText());

					if (currentPage > maxPages || currentPage < 1)
					{
						JOptionPane.showOptionDialog(getContentPane(), translations[137], translations[114], JOptionPane.YES_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[84]}, translations[84]);
						pageTextField.setText(currentDisplayedPage);
					}
					else
					{
						if (currentPage != Integer.parseInt(currentDisplayedPage))
						{
							fireSearchListSelectionListener = false; // Version 1.7
							try
							{
								final Statement stmt = sharedDBConnection.createStatement();
								final ResultSet rs = stmt.executeQuery("SELECT content FROM b" + bookId + " WHERE page=" + pageTextField.getText());
								if (rs.next())
								{
									// Version 1.7
									final Reader description = rs.getCharacterStream("content");
									final char[] arr = new char[2 * 1024];
									final StringBuilder buf = new StringBuilder();
									int numChars;

									while ((numChars = description.read(arr, 0, arr.length)) > 0)
										buf.append(arr, 0, numChars);

									setText(buf.toString(), "text/plain", programFolder + "images/save.png", translations[70]); // Version 1.7
									displayEditTextPane.setCaretPosition(0);
								}
								else
									setText("", "text/plain", programFolder + "images/save.png", translations[70]);

								currentDisplayedPage = pageTextField.getText();
								stmt.close();
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
							}
						}
					}
				}
			}
		});

		pageTextField.addKeyListener(new KeyListener()
		{
			public void keyTyped(KeyEvent e)
			{
				int k = e.getKeyChar();
				if (!(k > 47 && k < 58)) // i.e. don't pass the character to be written in the field
				{
					if (k != 8 && k != KeyEvent.VK_ENTER && k != KeyEvent.VK_DELETE)
					{
						e.setKeyChar((char) KeyEvent.VK_CLEAR); // Or consume()
						Toolkit.getDefaultToolkit().beep();
					}
				}
			}

			public void keyPressed(KeyEvent e)
			{
			}

			public void keyReleased(KeyEvent e)
			{
			}
		});

		enableEditPanel(false, translations[70]);

		final JLabel pageLabel = new JLabel("/");
		pageTextField.setHorizontalAlignment(JTextField.CENTER);
		pagesTextField.setHorizontalAlignment(JTextField.CENTER);
		//pageTextField.setPreferredSize(new Dimension(pageTextField.getWidth(), backButton.getPreferredSize().height));
		pageTextField.setPreferredSize(backButton.getPreferredSize());
		pagesTextField.setPreferredSize(backButton.getPreferredSize());
		pagesTextField.setEditable(false);

		final JPanel controlPanel_center = new JPanel();
		controlPanel_center.add(backButton);
		controlPanel_center.add(pageTextField);
		controlPanel_center.add(pageLabel);
		controlPanel_center.add(pagesTextField);
		controlPanel_center.add(forwardButton);

		final JPanel controlPanel_west = new JPanel();
		controlPanel_west.add(viewButton);
		controlPanel_west.add(saveButton);

		final JPanel controlPanel = new JPanel(new BorderLayout());
		controlPanel.add(controlPanel_center, BorderLayout.CENTER);
		if (language != lang.English) controlPanel.add(controlPanel_west, BorderLayout.WEST);
		else controlPanel.add(controlPanel_west, BorderLayout.EAST);
		displayEditPanel.add(controlPanel, BorderLayout.SOUTH);

		final JSplitPane splitPane1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
		splitPane1.setResizeWeight(.5D);
		//splitPane1.setDividerSize(5);
		splitPane1.setTopComponent(displayEditPanel);
		splitPane1.setBottomComponent(searchPanel);

		final JSplitPane splitPane2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		//splitPane2.setDividerSize(5);
		getContentPane().add(splitPane2);

		//getContentPane().add(decoratePanel, BorderLayout.CENTER);

		if (language == lang.English)
		{
			splitPane2.setResizeWeight(0D);
			//getContentPane().add(listPanel, BorderLayout.WEST);
			splitPane2.setLeftComponent(listPanel);
			splitPane2.setRightComponent(splitPane1);
		}
		else
		{
			splitPane2.setResizeWeight(1D);
			splitPane2.setLeftComponent(splitPane1);
			splitPane2.setRightComponent(listPanel);

			//getContentPane().add(listPanel, BorderLayout.EAST);
			getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		}

		createNodes();

		setVisible(true);
		//createBufferStrategy(2);

		// Version 1.5, Speeding up displaying the results.
		//final FontMetrics fm = searchResultsList.getGraphics().getFontMetrics(); // Version 1.7
		//final Insets m = com.alee.laf.list.WebListElement.getMargin();
		//searchResultsList.setFixedCellHeight(m.top+fm.getHeight()+m.bottom);
		//searchResultsList.setFixedCellHeight(fm.getHeight()+2);
		//searchResultsList.setFixedCellWidth(2500); // To be bigger than any result. If not the HTML will try to wrap the text resulting in wrong alignment. No HTML RTL support [http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4296022]
		searchResultsList.setPrototypeCellValue("إن الحمد لله، نحمدُه ونستغفره ونستعينه ونستهديه ونعوذُ بالله من شرورِ أنفسنا ومن سيئاتِ أعمالنا، من يهْدِ اللهُ فلا مضِلَّ له ومن يضلل فلا هادي له.");
		//searchResultsList.setCellRenderer(new SearchListRenderer());
		//searchResultsList.setDoubleBuffered(true);

		/* Version 1.4, Removed
    	// Check the existing of the documents
    	try
		{
			final Connection con = DriverManager.getConnection("jdbc:derby:indexerDatabase");
			final Statement stmt = con.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT * FROM "+bookTableName);

			final Vector<String> deletedDocumentfileNames = new Vector<String>();
			final Vector<String> deletedDocumentfileCategories = new Vector<String>();
			final Vector<String> deletedDocumentfilePaths = new Vector<String>();
			final Vector<String> deletedDocumentfileParent = new Vector<String>();// Version 1.4

			File file;
			while(rs.next())
			{
				file = new File(rs.getString("path"));
				if(!file.exists())
				{
					deletedDocumentfileNames.addElement(rs.getString("name"));
					deletedDocumentfileCategories.addElement(rs.getString("category"));
					deletedDocumentfilePaths.addElement(rs.getString("path"));
					deletedDocumentfileParent.addElement(rs.getString("parent"));// Version 1.4
				}
			}

			rs.close();
			stmt.close();
			con.close();

			if(deletedDocumentfileNames.size() > 0)
			{
				String deletedItemsLabel = "";
				for(int i=0; i<deletedDocumentfileNames.size(); i++)
					// Version 1.4, Add deletedDocumentfileParent to display the parent as well.
					deletedItemsLabel = deletedItemsLabel + lineSeparator + translations[27] + deletedDocumentfileCategories.elementAt(i) + translations[28] + (deletedDocumentfileParent.elementAt(i).equals("")?deletedDocumentfileNames.elementAt(i):(deletedDocumentfileParent.elementAt(i)+translations[2]+deletedDocumentfileNames.elementAt(i))) + ".";

				// Version 1.3, Enable scrolling in JOptionPane when it exceeds screen limit using getOptionPaneScrollablePanel().
				final Object[] options = {translations[0], translations[1]};
				final int choice = JOptionPane.showOptionDialog(getContentPane(), getOptionPaneScrollablePanel(translations[29], deletedItemsLabel.substring(2), translations[30]), translations[7], JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
				if(choice == JOptionPane.YES_OPTION)
				{
					final JDialog deleteProgressDialog = new JDialog(ArabicIndexer.this, translations[51], true);
					deleteProgressDialog.setLayout(new FlowLayout());
					deleteProgressDialog.setResizable(false);

					// To not enable the user to use the DB since its locked by the thread.
					deleteProgressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

					final JProgressBar deleteProgressBar = new JProgressBar();
					deleteProgressBar.setString(translations[51]);
					deleteProgressBar.setStringPainted(true);
					deleteProgressBar.setIndeterminate(true);
					deleteProgressDialog.getContentPane().add(deleteProgressBar);
					if(language)deleteProgressDialog.getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

					deleteProgressDialog.pack();
					centerInScreen(deleteProgressDialog);

					final class thread extends SwingWorker<Void, Void>
					{
						public Void doInBackground()
						{
							try
							{
								final Connection con = DriverManager.getConnection("jdbc:derby:indexerDatabase");
								final IndexReader indexReader  = IndexReader.open(indexName);
								final PreparedStatement ps = con.prepareStatement("DELETE FROM "+bookTableName+" WHERE path = ?");
								for(int i=0; i<deletedDocumentfilePaths.size(); i++)
								{
									// Deleting the indexing files.
									indexReader.deleteDocuments(new Term("path", deletedDocumentfilePaths.elementAt(i)));
									new File("indexingFiles"+fileSeparator+(deletedDocumentfilePaths.elementAt(i).replace(fileSeparator.charAt(0), '-')).replace(':', '_') + ".txt").delete();
									ps.setString(1, deletedDocumentfilePaths.elementAt(i));
									ps.execute();
								}
								ps.close();
								con.close();
								indexReader.close();

								// To refresh detailList
								createNodes(treeRoot);
							}
							catch(SQLException ex){System.err.println("SQLException: " + ex.getMessage());}
							catch(IOException ioe){System.err.println("IOException: " + ioe.getMessage());}
							return null;
						}

						protected void done()
						{
							deleteProgressDialog.dispose();
							deleteProgressDialog.setVisible(false);
						}
					}
					(new thread()).execute();
					deleteProgressDialog.setVisible(true);
				}
			}
		}
		catch(SQLException ex){System.err.println("SQLException: " + ex.getMessage());}
		*/

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				shutdown();
			}
		});

		// Version 1.4, For history list. It should be at the end after setVisible to calculate
		//if(language!=lang.English)historyList.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		//historyList.setFixedCellWidth(searchTextField.getBounds().width - 27);
		//historyPopup.setPreferredSize(new Dimension(searchTextField.getBounds().width, 120));
		addComponentListener(new ComponentAdapter()
		{
			// Resizing history Popup when resizing JFrame
			public void componentResized(ComponentEvent e)
			{
				//historyList.setFixedCellWidth(searchTextField.getBounds().width-27);
				//historyPopup.setPreferredSize(new Dimension(searchTextField.getBounds().width+1, 120));
				historyPopup.setPreferredSize(new Dimension(searchTextField.getBounds().width + 24, 200)); // TODO: change when spliter is changed as well
			}
		});

		final JScrollPane sp1 = new JScrollPane(historyList);
		sp1.setBorder(null);
		historyPopup.add(sp1);

		if(isMAC)
		{
			try
			{
				// Version 1.5, Open biuf files in Mac with .app Info.plist.
				// Version 2.0, reuse java.awt.desktop instead com.apple.eawt
				final Desktop desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.APP_ABOUT))
				{
					desktop.setAboutHandler(e ->
					{
						JOptionPane.showOptionDialog(getContentPane(),
								translations[145] + System.lineSeparator() +
										translations[146] + System.lineSeparator() +
										translations[147] + System.lineSeparator() +
										translations[148] + System.lineSeparator() +
										translations[149], translations[144], JOptionPane.YES_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{translations[44]}, translations[44]);
					});
				}

				/*
				This is not working since we have our own bash script launcher 'myJavaApplicationStub' instead of Java stub which I do not know where to get it (maybe from jpackage)
				Because myJavaApplicationStub is a shell script, it does not know how to receive AppleEvents (e.g. OpenFileEvent), so they just sit there unhandled. You need an executable that knows how to receive them and pass them to your Java program for processing, perhaps some sort of native "Java stub"
				TODO: -> https://eschmann.io/posts/javafx-mac-os-custom-file-type/

				if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE))
				{
					desktop.setOpenFileHandler(e ->
					{
						for (File element : e.getFiles())
							updateFiles.add(element.toString());
						importMenuItem.doClick();
					});
				}
				*/

				if (desktop.isSupported(Desktop.Action.APP_PREFERENCES))
					desktop.setPreferencesHandler(e -> new Setting(ArabicIndexer.this));

				if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER))
				{
					desktop.setQuitHandler((e, response) ->
					{
						shutdown();
					});
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		// Version 1.4
		if (!updateFiles.isEmpty())
			importMenuItem.doClick();

		final TransferHandler dnd = new TransferHandler()
		{
			@Override
			public boolean canImport(TransferSupport support)
			{
				if (!support.isDrop())
					return false;

				if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) // Supported by Windows/Mac
				{
					try
					{
						// Under Windows, the Transferable instance
						// will have transfer data ONLY while the mouse button is
						// depressed.  However, when the user releases the mouse
						// button, this method will be called one last time.  And when
						// when this method attempts to getTransferData, Java will throw
						// an 'InvalidDnDOperationException: No drop current'.  Since we know that the
						// exception is coming, we simply catch it and ignore it.
						// Normal behavior, do not try to remove this exception
						final List<File> fileList = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
						for (File file : fileList)
						{
							if (!file.getName().endsWith(".biuf"))
								return false;
						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					return true;
				}
				else
				{
					if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) // Supported by Linux
					{
						try
						{
							final String[] paths = ((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim().split("\\r?\\n");
							for (String p : paths)
								if (!p.endsWith(".biuf"))
									return false;
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean importData(TransferSupport support)
			{
				try
				{
					if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) // Supported by Windows/Mac
					{
						final List<File> fileList = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
						for (File file : fileList)
							updateFiles.add(file.getAbsolutePath());

						final Thread thread = new Thread()
						{
							public void run()
							{
								importMenuItem.doClick();
							}
						};
						thread.start();
						return true;
					}
					else // Drop from GNOME
					{
						if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) // Supported by Linux
						{
							final String[] paths = ((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim().split("\\r?\\n");
							for (String p : paths)
								if (p.startsWith("file://")) // No Need for this
									updateFiles.add(new URI(p).getPath());

							final Thread thread = new Thread()
							{
								public void run()
								{
									importMenuItem.doClick();
								}
							};
							thread.start();
							return true;
						}
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				return false;
			}
		};
		setTransferHandler(dnd); // Version 1.5, capability to drag files. Works in Windows/Mac/Linux

		// Version 1.6, Open files while the program is running.
		final Thread thread = new Thread()
		{
			public void run()
			{
				try
				{
					final RandomAccessFile logFile = new RandomAccessFile(programFolder + "temp/logFile", "rw");
					FileChannel logChannel = logFile.getChannel();

					while (true)
					{
						final FileLock logLock = logChannel.lock(); // will be blocked until it can lock the file for writing

						try
						{
							// Compare the length of the file to the file pointer
							final long fileLength = logFile.length();
							String files = "";
							logFile.seek(0L);
							for (long filePointer = 0L; filePointer < fileLength; filePointer = logFile.getFilePointer())
								// There is data to read
								files += logFile.readUTF(); // You need one readUTF() for every writeUTF() !!!

							if (!files.isEmpty())
							{
								updateFiles.addAll(Arrays.asList(files.split(System.lineSeparator())));
								SwingUtilities.invokeLater(new Runnable()
								{
									public void run()
									{
										importMenuItem.doClick();
									}
								});
								logChannel.truncate(0L);
							}
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}

						logLock.release();

						// Sleep for the specified interval
						sleep(1000L);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		thread.start();
	}

	// Version 1.8
	void pre_shutdown()
	{
		// Version 1.4, To store the history of the search entries
		try
		{
			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(programFolder + "setting/history.txt"), StandardCharsets.UTF_8);
			for (int i = 0; i < historyListModel.getSize() - 1; i++)
				out.write(historyListModel.elementAt(i) + System.lineSeparator());

			// In case it is one element or the last element.
			if (!historyListModel.isEmpty())
				out.write(historyListModel.lastElement());

			out.close();
			indexWriter.close();

			if (language != lang.English)
			{
				arabicRootsWriter.close();
				arabicLuceneWriter.close();
			}

			sharedDBConnection.createStatement().execute("SHUTDOWN"); // Version 1.8, to close the DB before System.exit() to not trigger the shutdown hook which will take time and affect relaunch (in case of update or language change).
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	void shutdown()
	{
		pre_shutdown();
		System.exit(0);
	}

	// Version 1.6
	void enableEditPanel(final boolean enable, final String note)
	{
		viewButton.setEnabled(enable);
		saveButton.setEnabled(enable);
		backButton.setEnabled(enable);
		forwardButton.setEnabled(enable);
		pageTextField.setEditable(enable);

		setText("", "text/plain", programFolder + "images/save.png", note);
		displayEditTextPane.setEditable(enable); // Version 1.7, moved here since setText() includes setEditable(true).
		pageTextField.setText("");
		pagesTextField.setText("");
		currentDisplayedPage = "";
	}

	// Version 1.7
	void setText(final String text, final String type, final String image, final String tooltip)
	{
		displayEditTextPane.setContentType(type);
		displayEditTextPane.setText(text);
		saveButton.setIcon(new ImageIcon(image));
		saveButton.setToolTipText(tooltip);

		// http://javatechniques.com/blog/setting-jtextpane-font-and-color/
		if (type.equals("text/html"))
		{
			displayEditTextPane.setEditable(false);

	        /* Version 1.7, Removed
            final MutableAttributeSet attrs = displayEditTextPane.getInputAttributes();
            StyleConstants.setFontFamily(attrs, "KFGQPC Uthman Taha Naskh");
            StyleConstants.setFontSize(attrs, 23);  // 23 but the normal font is 26 !!!! different measures. maybe pt px

            StyledDocument doc = displayEditTextPane.getStyledDocument();
            doc.setCharacterAttributes(0, doc.getLength() + 1, attrs, false);
            */
		}
		else
			displayEditTextPane.setEditable(true);
	}

	// Version 1.6
	void viewDocument(String path, final String page)
	{
		path = path.replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9

		final String[] translations = StreamConverter(programFolder + "language/viewDocument" + language + ".txt");

		// Version 1.3, To handle Manuscripts.
		if (new File(path).isDirectory())
		{
			final File[] files = new File(path).listFiles();
			boolean pageFound = false;
			if(files != null)
			{
				for (File element : files)
				{
					try
					{
						// split() will not work here !!
						final StringTokenizer token = new StringTokenizer(element.getName(), ".");
						final String pageNumber = token.nextToken();
						if (pageNumber.equals(page))
						{
							Desktop.getDesktop().open(new File(path + File.separator + element.getName()));
                        /* Version 1.5, Replaced with the above.
                        if(windows)
                            //Runtime.getRuntime().exec("\"bin"+fileSeparator+"irfanView"+fileSeparator+"i_view32.exe\" \""+searchResultsPathVector.elementAt(searchResultSelectedIndex)+fileSeparator+files[j].getName()+"\"");
                            Runtime.getRuntime().exec(new String []{"rundll32.exe", "C:\\WINDOWS\\System32\\shimgvw.dll,ImageView_Fullscreen", searchResultsPathVector.elementAt(searchResultSelectedIndex)+File.separator+element.getName()});
                        else
                        {
                            if(isMAC)
                                Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "open", "-a", "preview", searchResultsPathVector.elementAt(searchResultSelectedIndex)+File.separator+element.getName()});
                            else
                            {
                                if(isLinux)
                                {
                                    // For Gnome
                                    Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "eog", searchResultsPathVector.elementAt(searchResultSelectedIndex)+File.separator+element.getName()});

                                    // For KDE
                                    Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "gwenview", searchResultsPathVector.elementAt(searchResultSelectedIndex)+File.separator+element.getName()});
                                }
                                else
                                    Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "eog", searchResultsPathVector.elementAt(searchResultSelectedIndex)+File.separator+element.getName()});
                            }
                        }
                        */
							pageFound = true;
							break;
						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}

			if (!pageFound)
				JOptionPane.showOptionDialog(getContentPane(), translations[0] + page + translations[1] + System.lineSeparator() + path, translations[2], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[3]}, translations[3]);
		}
		else
		{
			if (new File(path).exists())
			{
				try
				{
					if (path.endsWith("pdf") || path.endsWith("PDF"))
					{
						if (isWindows)
						{
							// This command hang because the error buffer is filled even if we use cmd.exe which should create new process without any relation with the main java process. you need to capture the error stream and clear it.
							//Runtime.getRuntime().exec(new String []{new File("bin/SumatraPDF.exe").getAbsolutePath(), "-page", page, path}); // Version 1.6, Replace Foxit with SumatraPDF

							// Version 1.7
							final Process proc = Runtime.getRuntime().exec(new String[]{new File(programFolder + "bin/SumatraPDF.exe").getAbsolutePath(), "-page", page, path}); // Version 1.6, Replace Foxit with SumatraPDF

							// Capture the Error/Output streams from the SumatraPDF process
							class StreamGrabber extends Thread
							{
								final InputStream is;
								final String type;

								StreamGrabber(InputStream is, String type)
								{
									this.is = is;
									this.type = type;
								}

								public void run()
								{
									try
									{
										final BufferedReader br = new BufferedReader(new InputStreamReader(is));
										String line;
										while ((line = br.readLine()) != null)
											System.out.println(type + ">" + line);
										br.close();
										is.close();
									}
									catch (IOException ioe)
									{
										ioe.printStackTrace();
									}
								}
							}

							final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "ERROR"); // any error message?
							final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "OUTPUT"); // any output?

							errorGrabber.start();
							outputGrabber.start();

							// any error???
							// No need to wait, otherwise it should be in thread. or kill it while exiting the program.
							//int exitVal = proc.waitFor();
							//System.out.println("ExitValue: " + exitVal);
						}
						else
						{
							// Version 1.3
							if (isMAC)
								Runtime.getRuntime().exec(new String[]{new File(programFolder + "preview_pdf.sh").getAbsolutePath(), path, page});
							else
							{
								/* Version 1.4, xpdf is no longer working as a standalone/portable application
								if(isLinux)
									Runtime.getRuntime().exec(new String []{new File("bin/xpdf.Linux").getAbsolutePath(), "\""+searchResultsPathVector.elementAt(searchResultSelectedIndex)+"\"", searchResultsPageVector.elementAt(searchResultSelectedIndex)});
								else
									Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "bin"+File.separator+"xpdf.Solaris", "\""+searchResultsPathVector.elementAt(searchResultSelectedIndex)+"\"", searchResultsPageVector.elementAt(searchResultSelectedIndex)});
								*/

								try
								{
									Runtime.getRuntime().exec(new String[]{"evince", "--page-label=" + page, path});
								}
								catch (Exception ae)
								{
									ae.printStackTrace();
									Runtime.getRuntime().exec(new String[]{"okular", "-p", page, path});
								}
							}
						}
					}
				}
				catch (Exception ae)
				{
					ae.printStackTrace();
				}
			}
			else
				JOptionPane.showOptionDialog(getContentPane(), translations[4] + System.lineSeparator() + path, translations[2], JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{translations[3]}, translations[3]);
		}
	}

	// Version 1.4, To send a command and get the output
	/*
	public static String cmdExec(final String cmdLine[])
	{
		String line;
		String output = "";
		try
		{
			final Process p = Runtime.getRuntime().exec(cmdLine);
			final BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null)
				output += (line + lineSeparator);
			input.close();
		}
		catch(Exception e){e.printStackTrace();}
		return output;
	}
	*/

	// Version 1.4
	/* Version 1.4, Replaced with Desktop.edit()
	public void openInEditor(final String fileName)
	{
		try
		{
			if(windows)
				Runtime.getRuntime().exec(new String []{"cmd.exe", "/C", "notepad", "\"indexingFiles"+File.separator+fileName+"\""});
			else
			{
				// Version 1.3
				if(isMAC)
					Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "open", "-a", "textedit", "\"indexingFiles"+File.separator+fileName+"\""});
				else
				{
					if(isLinux)
					{
						// For Gnome
						Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "gedit", "\"indexingFiles"+File.separator+fileName+"\""});

						// For KDE
						Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "kate", "\"indexingFiles"+File.separator+fileName+"\""});
					}
					else
						// i.e. Unix
						Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "gedit", "\"indexingFiles"+File.separator+fileName+"\""});
				}
			}
		}
		catch(Exception e){e.printStackTrace();}
	}
	*/

	// Version 1.3, Enable scrolling in JOptionPane when it exceeds screen limit.
	public static JPanel getOptionPaneScrollablePanel(final String s1, final String s2)
	{
		final JPanel optionDialogPanel = new JPanel(new BorderLayout());
		optionDialogPanel.add(new JLabel(s1), BorderLayout.NORTH);

		final JTextArea textArea = new JTextArea(s2);
		textArea.setEditable(false);
		textArea.setBorder(null);

		final JScrollPane scrollPane = new JScrollPane(textArea);
		optionDialogPanel.add(scrollPane, BorderLayout.CENTER);
		scrollPane.setBorder(null);

		final Dimension dim = optionDialogPanel.getPreferredSize();
		if (dim.getHeight() > (screenSize.getHeight() - 200))
		{
			if (dim.getWidth() > (screenSize.getWidth() - 50))
				scrollPane.setPreferredSize(new Dimension((int) screenSize.getWidth() - 50, (int) screenSize.getHeight() - 200));
			else
				scrollPane.setPreferredSize(new Dimension((int) scrollPane.getPreferredSize().getWidth() + 20, (int) screenSize.getHeight() - 200));
		}

		if (dim.getWidth() > (screenSize.getWidth() - 50))
			scrollPane.setPreferredSize(new Dimension((int) screenSize.getWidth() - 50, (int) scrollPane.getPreferredSize().getHeight() + 20));

		return optionDialogPanel;
	}

	// This function is used to index a document.
	private boolean addDocumentToIndex(final String insertedBookName, final String insertedBookPath, final String insertedBookCategory, final String insertedBookAuthor, final String insertedBookParent, final JProgressBar pageProgressBar, final IndexWriter writer, final int insertedBookId)
	{
		// Version 1.5, Removed since the pdf file is not always available. In addition it is slow. faster to parse the txt file.
		//final int pageCount = getDocumentPageCount(insertedBookPath);

		// we remove it since the indexing file is their i.e. it is created previously,
		// although in the progressbar the rang will be 0 but it will be indexed
		//if(pageCount!=0)
		{
			try
			{
				// Version 1.6
				final Statement stmt = sharedDBConnection.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM b" + insertedBookId);
				rs.next();
				final int pageCount = rs.getInt(1);

                /* Version 1.6, Replaced with a DB
				// Opening the file is done at the beginning so that if the file is not exists, no locking of the lucene engine.
				final String indexingBookName = (insertedBookPath.replace(File.separatorChar, '-')).replace(':', '_') + ".txt";
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("indexingFiles/"+indexingBookName), "UTF-8"));

				int pageCount = 0;
				while(in.ready())
					if(in.readLine().startsWith("ööööö"))
						pageCount++;

				in.close();
				in = new BufferedReader(new InputStreamReader(new FileInputStream("indexingFiles/"+indexingBookName), "UTF-8"));
				*/

				// Version 1.3, change (pageCount)  to  ((pageCount==0)?1:pageCount) to not display 'squar' in the progress bar.
				// If there is no pages in the indexing it will not complete which is not wrong.
				// Version 1.5, Moved here.
				pageProgressBar.setMaximum((pageCount == 0) ? 1 : pageCount);
				pageProgressBar.setValue(0);

				// Version 1.7, Lucene 4.0 migration
				// StringField.TYPE_STORED instead of "Field.Store.YES, Field.Indexed.NOT_ANALYZED_NO_NORMS", No Norms is better for performance and boosting is not important especially for Arabic
				final Document doc = new Document();
				doc.add(new Field("name", insertedBookName, StringField.TYPE_STORED));
				doc.add(new Field("path", insertedBookPath, StringField.TYPE_STORED));
				doc.add(new Field("parent", insertedBookParent, StringField.TYPE_STORED));
				doc.add(new Field("category", insertedBookCategory, StringField.TYPE_STORED));
				doc.add(new Field("author", insertedBookAuthor, StringField.TYPE_STORED));
				doc.add(new Field("id", String.valueOf(insertedBookId), StringField.TYPE_STORED));

				//int pageNo = 1;
				//String pageContent = "";
				int pageProgressBarCounter = 0;
				rs = stmt.executeQuery("SELECT * FROM b" + insertedBookId);
				//while(in.ready())
				while (rs.next())
				{
					//final String str = in.readLine();
					final int pageNo = rs.getInt("page");
					//if(str.contains("ööööö"))
					{
						// Version 1.4, Enhance the indexing for fast search. The idea is to store each page in a separate document.
						doc.add(new Field("page", String.valueOf(pageNo), StringField.TYPE_STORED));

						// Version 1.5, 'Field.TermVector.WITH_POSITIONS_OFFSETS' to store the TermVectors (for Highlighter) at the index time and not at the search time. For performance.
						// Version 1.7
						final Reader description = rs.getCharacterStream("content");
						final char[] arr = new char[2 * 1024];
						final StringBuilder buf = new StringBuilder();
						int numChars;

						while ((numChars = description.read(arr, 0, arr.length)) > 0)
							buf.append(arr, 0, numChars);

						doc.add(new Field("pageContent", buf.toString(), TextField.TYPE_STORED)); // TODO: Not speeding up the highlighter  , Field.TermVector.WITH_POSITIONS_OFFSETS. TextField.TYPE_STORED is "Field.Store.YES, Field.Index.ANALYZED"

						writer.addDocument(doc);

						doc.removeField("page");
						doc.removeField("pageContent");

						//pageContent = "";
						pageProgressBar.setValue(++pageProgressBarCounter);
						//pageNo++;
					}
					//else
					//	pageContent = pageContent + ' ' + str;
				}
				writer.commit(); // Version 1.5
				rs.close();
				stmt.close();

				// This is to indicate that the book is added.
				return true;
			}
			catch (Exception ioe)
			{
				ioe.printStackTrace();

				// i.e. The file is not there
				return false;
			}
		}
		//else
		//	System.out.println("You cannot index the indexing file since the document is missing: "+insertedBookPath);

		// i.e. The document is not there. PLEASE CHECK
		//return false;
	}

	// This function is used to get the number of pages in a PDF
	private static int getDocumentPageCount(final String documentPath)
	{
		try
		{
			// Version 1.3, This will count the number of images in the Manuscript folder.
			if (new File(documentPath).isDirectory())
			{
				final File[] files = new File(documentPath).listFiles();
				int count = 0;
				if(files != null)
				{
					for (File element : files)
					{
						// This try-catch to avoid breaking the creation of indexing file.
						try
						{
							// split() will not work here !!
							final StringTokenizer token = new StringTokenizer(element.getName(), ".");
							final int pictureNumber = Integer.parseInt(token.nextToken());

							if (pictureNumber > count)
								count = pictureNumber;
						}
						catch (NumberFormatException nfe)
						{
							nfe.printStackTrace();
						}
					}
				}
				return count;
			}
			else
			{
				if (documentPath.endsWith(".pdf") || documentPath.endsWith(".PDF"))
				{
					/* Not working in many cases.
					long startTime = System.nanoTime();
					int count=0;
					final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(documentPath)));
					while(in.ready())
					{
						final String line = in.readLine();
						int index;
						if((index=line.indexOf("/Count"))>-1 && line.indexOf("/Parent")==-1 && line.indexOf("/Pages")>-1)
						{
							System.out.println(line);
							int pages = Integer.valueOf((line.substring(index+7)).split("/")[0]);
							if(pages > count)
								count = pages;
						}
					}
					in.close();
					System.out.println("Time: " + ((System.nanoTime() - startTime) / 1000000000.0));
					System.out.println(count);
					return count;
					*/

					// Version 1.6, The same as pdfbox but less size and maybe more mature.
					//final PdfReader doc = new PdfReader(documentPath); // Version 1.7, Remove: new FileInputStream(new File(documentPath)), it causes huge memory consumption, more than 3x.
					final PdfDocument doc = new PdfDocument(new PdfReader(documentPath)); // Version 2.0
					int count = doc.getNumberOfPages();
					doc.close(); // To free the in-memory representation of the PDF document.
					return count;

                    /*
                    // Version 1.6, Replaced by itextpdf because it is smaller, faster and more stable/tested.
					final org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(documentPath);
					int count = doc.getNumberOfPages();
					if(doc != null) doc.close(); // To free the in-memory representation of the PDF document.
					return count;
					*/

					/* Version 1.4, Replaced by PDDocument.getNumberOfPages()
					// Version 1.3, extends to work in all OSs.
					final Process ls_proc;
					if(windows)
						ls_proc = Runtime.getRuntime().exec(new String []{new File("bin/APGetPageCount.exe").getAbsolutePath(), "\""+documentPath+"\""});
					else
					{
						if(isMAC)
							ls_proc = Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "\"bin"+File.separator+"APGetPageCount.OSX\"", "\""+documentPath+"\""});
						else
						{
							if(isLinux)
								ls_proc = Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "\"bin"+File.separator+"APGetPageCount.Linux\"", "\""+documentPath+"\""});
							else
								// i.e. Unix
								ls_proc = Runtime.getRuntime().exec(new String []{"/bin/bash", "-c", "\"bin"+File.separator+"APGetPageCount.Solaris\"", "\""+documentPath+"\""});
						}
					}

					// This causes the current thread to wait, if necessary, until the process has terminated.
					ls_proc.waitFor();
					final BufferedReader ls_in = new BufferedReader(new InputStreamReader(ls_proc.getInputStream()));

					while(ls_in.ready())
					{
						final String ls_str = ls_in.readLine();
						if(ls_str.indexOf("PageCount:") > -1)
						{
							final StringTokenizer token = new StringTokenizer(ls_str, ":");
							token.nextToken();
							return Integer.valueOf(token.nextToken().trim());
						}
					}
					ls_in.close();
					*/
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return 0;
	}

	/*
	static int calculatedPageCount  = 0;
	static class AsyncPipe implements Runnable
    {
    	private final OutputStream ostrm_;
        private final InputStream istrm_;

        public AsyncPipe(InputStream istrm, OutputStream ostrm)
        {
            istrm_ = istrm;
            ostrm_= ostrm;
        }

        public void run()
        {
            try
            {
            	calculatedPageCount = 0;
                final byte[] buffer = new byte[1024];
                String shellOutput = "";
                for(int length = 0; (length = istrm_.read(buffer)) != -1;)
                	shellOutput = shellOutput + new String(buffer);
					//ostrm_.write(buffer, 0, length);

                System.out.println(shellOutput);
                calculatedPageCount = shellOutput.split("PAGE ").length - 1;
                System.out.println(calculatedPageCount);
            }
            catch (Exception e){e.printStackTrace();}
        }
    }
    */

	// Version 1.4, Refresh the connection to take care of the updates
	private static void reopenIndexSearcher()
	{
		try
		{
			defaultSearcher.getIndexReader().close();
			if (language == lang.English)
				defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "englishIndex").toPath())));
			else
			{
				arabicRootsSearcher.getIndexReader().close();
				arabicLuceneSearcher.getIndexReader().close();

				defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicIndex").toPath())));
				arabicRootsSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicRootsIndex").toPath())));
				arabicLuceneSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(programFolder + "arabicLuceneIndex").toPath())));
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void createNodes()
	{
		//searchTree.removeCheckStateChangeListener(searchTreeChangeListener);
		//authorSearchTree.removeCheckStateChangeListener(authorSearchTreeChangeListener);

		treeRoot.removeAllChildren();
		searchRoot.removeAllChildren();
		authorTreeRoot.removeAllChildren();
		authorSearchRoot.removeAllChildren();

		try
		{
			final Statement stmt1 = sharedDBConnection.createStatement();
			final Statement stmt2 = sharedDBConnection.createStatement();
			final Statement stmt3 = sharedDBConnection.createStatement();

			// Book Tree
			ResultSet rs1 = stmt1.executeQuery("SELECT category FROM " + bookTableName + " GROUP BY category ORDER BY category");
			while (rs1.next())
			{
				final String categoryName = rs1.getString("category");

				final DefaultMutableTreeNode category = new DefaultMutableTreeNode(new NodeInfo(categoryName, "", "category", "", "", "category", 0));
				final DefaultMutableTreeNode searchCategory = new DefaultMutableTreeNode(new NodeInfo(categoryName, "", "searchCategory", "", "", "searchCategory", 0));
				treeRoot.add(category);
				searchRoot.add(searchCategory);

				// Adding books which are just one volume.
				ResultSet rs2 = stmt2.executeQuery("SELECT id, name, path, author FROM " + bookTableName + " WHERE (category = '" + categoryName + "' AND parent = '') ORDER BY name");
				while (rs2.next())
				{
					final String name = rs2.getString("name");
					final String path = rs2.getString("path"); // Version 1.9
					final int id = rs2.getInt("id"); // Version 2.1
					final String absolutePath = path.replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9
					final String author = rs2.getString("author");
					category.add(new DefaultMutableTreeNode(new NodeInfo(name, "", path, categoryName, author, absolutePath, id)));
					searchCategory.add(new DefaultMutableTreeNode(new NodeInfo(name, "", path, categoryName, author, absolutePath, id)));
				}

				// Adding books which are more than one volume.
				rs2 = stmt2.executeQuery("SELECT parent, author FROM " + bookTableName + " WHERE (category = '" + categoryName + "' AND parent != '') GROUP BY parent, author"); // Version 1.7, GROUP BY should be with the same columns in SELECT
				while (rs2.next())
				{
					final String bookParent = rs2.getString("parent");
					final String author = rs2.getString("author");

					final DefaultMutableTreeNode parent = new DefaultMutableTreeNode(new NodeInfo(bookParent, "", "parent", categoryName, author, "parent", 0));
					category.add(parent);
					searchCategory.add(new DefaultMutableTreeNode(new NodeInfo(bookParent, "", "", categoryName, author, "", 0)));

					final ResultSet rs3 = stmt3.executeQuery("SELECT * FROM " + bookTableName + " WHERE (category = '" + categoryName + "' AND parent = '" + bookParent + "' AND author = '" + author + "') ORDER BY name"); // Version 1.7, ... AND author = '"+author+"' ...
					while (rs3.next())
					{
						final String path = rs3.getString("path"); // Version 1.9
						final int id = rs3.getInt("id"); // Version 2.1
						final String absolutePath = path.replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9
						parent.add(new DefaultMutableTreeNode(new NodeInfo(rs3.getString("name"), bookParent, path, categoryName, author, absolutePath, id)));
					}

					rs3.close();
				}
				rs2.close();
			}

			// Version 1.7, Author Tree
			rs1 = stmt1.executeQuery("SELECT author FROM " + bookTableName + " GROUP BY author ORDER BY author");
			while (rs1.next())
			{
				final String authorName = rs1.getString("author");

				final DefaultMutableTreeNode author = new DefaultMutableTreeNode(new NodeInfo(authorName, "", "author", "", "", "author", 0));
				final DefaultMutableTreeNode searchAuthor = new DefaultMutableTreeNode(new NodeInfo(authorName, "", "searchAuthor", "", "", "searchAuthor", 0));
				authorTreeRoot.add(author);
				authorSearchRoot.add(searchAuthor);

				// Adding books which are just one volume.
				ResultSet rs2 = stmt2.executeQuery("SELECT id, name, path, category FROM " + bookTableName + " WHERE (author = '" + authorName + "' AND parent = '') ORDER BY name");
				while (rs2.next())
				{
					final String name = rs2.getString("name");
					final String path = rs2.getString("path"); // Version 1.9
					final int id = rs2.getInt("id"); // Version 2.1
					final String absolutePath = path.replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9
					final String category = rs2.getString("category");
					author.add(new DefaultMutableTreeNode(new NodeInfo(name, "", path, category, authorName, absolutePath, id)));
					searchAuthor.add(new DefaultMutableTreeNode(new NodeInfo(name, "", path, category, authorName, absolutePath, id)));
				}

				// Adding books which are more than one volume.
				rs2 = stmt2.executeQuery("SELECT parent, category FROM " + bookTableName + " WHERE (author = '" + authorName + "' AND parent != '') GROUP BY parent, category"); // Version 1.7, GROUP BY should be with the same columns in SELECT
				while (rs2.next())
				{
					final String bookParent = rs2.getString("parent");
					final String category = rs2.getString("category");

					final DefaultMutableTreeNode parent = new DefaultMutableTreeNode(new NodeInfo(bookParent, "", "parent", category, authorName, "parent", 0));
					author.add(parent);
					searchAuthor.add(new DefaultMutableTreeNode(new NodeInfo(bookParent, "", /* Version 1.7 */"", category, authorName, "", 0)));

					final ResultSet rs3 = stmt3.executeQuery("SELECT * FROM " + bookTableName + " WHERE (author = '" + authorName + "' AND parent = '" + bookParent + "' AND category = '" + category + "') ORDER BY name"); //Version 1.7, ... AND category = '"+category+"' ...
					while (rs3.next())
					{
						final String path = rs3.getString("path"); // Version 1.9
						final int id = rs3.getInt("id"); // Version 2.1
						final String absolutePath = path.replaceFirst("root:pdf", eProgramFolder + "pdf"); // Version 1.9
						parent.add(new DefaultMutableTreeNode(new NodeInfo(rs3.getString("name"), bookParent, path, category, authorName, absolutePath, id)));
					}

					rs3.close();
				}
				rs2.close();
			}

			stmt1.close();
			stmt2.close();
			stmt3.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		orderButton.doClick();

		//searchTree.addCheckStateChangeListener(searchTreeChangeListener);
		//authorSearchTree.addCheckStateChangeListener(authorSearchTreeChangeListener);
	}

	/*
	 * Making the parameter is Component to cast all types of window
	 * e.g. JWindow, JFrame, JDialog, ... to work with all of them
	 */
	public static void centerInScreen(Component component)
	{
		final Rectangle bounds = component.getBounds();
		component.setLocation((screenSize.width - bounds.width) / 2, (screenSize.height - bounds.height) / 2);
	}

	// Function to read arabic translation files.
	public static String[] StreamConverter(final String filePath)
	{
		try
		{
			// Version 1.4
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8));
			final Vector<String> lines = new Vector<>();

			while (in.ready()) lines.addElement(in.readLine());
			in.close();

			return lines.toArray(new String[lines.size()]);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		System.exit(0);
		return null;
	}

	//https://github.com/mgarin/weblaf/blob/master/modules/ui/src/com/alee/laf/list/WebListCellRenderer.java
	// TODO: it is better for loading but it kills the performance once you start scrolling the list.
	static class SearchListRenderer extends WebStyledLabel implements ListCellRenderer
	{
		public SearchListRenderer()
		{
			super();

			//setOpaque(true);
			//putClientProperty("html.disable", Boolean.TRUE);
		}

		//protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
			setText(value.toString());

			// Border
			//final ListUI lui = list.getUI ();
			//final int sw = com.alee.laf.list.WebListStyle.selectionShadeWidth;
			//final int sw = StyleConstants.shadeWidth;
			final int sw = 2; // from the old src
			setMargin(sw + 8, sw + 10, sw + 8, sw + 10);

			// Orientation
			setComponentOrientation(list.getComponentOrientation());
			//applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

			return this;
		}
	}

	static String programFolder, eProgramFolder;

	// Version 1.4
	static private Vector<String> updateFiles;

	static private FileLock lock; // Global so that it will not be GC, otherwise lock will released
	static private FileChannel lockChannel;

	public static void main(String[] args)
	{
		try
		{
			// Version 1.7
			programFolder = new File(ArabicIndexer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath() + File.separator;
			eProgramFolder = programFolder.replace("\\", "\\\\"); // To escape special character e.g. '\'the path.

			// Version 1.6, Used to allow passing the files when clicking on them while the program is running.
			// This will prevent notifying the user that there is another running instance. We cannot notify the user here since it will be repeated in case of clicking many files at the same time in case the program was not running in windows.
			lockChannel = new RandomAccessFile(new File(programFolder + "temp/lockFile"), "rw").getChannel();
			lock = lockChannel.tryLock();
			if (lock == null) // i.e. another instance is running now
			{
				lockChannel.close();
				System.out.println("lock null");
				return;
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}

		updateFiles = new Vector<>(Arrays.asList(args));

        /*
        try
        {
            // Set cross-platform Java L&F (also called "Metal") for MAC since it is not the default.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        }
        catch(Exception e){e.printStackTrace();}
        //*/

        /*
        try
        {
            com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel laf = new com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel();
            UIManager.setLookAndFeel(laf);
            laf.getDefaults().put("defaultFont", new Font("Tahoma", Font.PLAIN, 12));

        /*
        for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
        {
            if("Nimbus".equals(info.getName()))
            {
                // Very difficult [http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/color.html]
                //UIManager.put("control", new Color(241, 240, 227));
                //UIManager.put("nimbusBase", new Color(200, 190, 157));
                //UIManager.put("nimbusBlueGrey", new Color(222, 173, 98));

                UIManager.setLookAndFeel(info.getClassName());
                break;
            }
        }
        */
		//}
		//catch(Exception e){e.printStackTrace();}
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				//com.alee.laf.WebLookAndFeel.labelFont = new Font ("Tahoma", Font.PLAIN, 12);
				//LanguageManager.setDefaultLanguage(LanguageConstants.ARABIC);
				WebLookAndFeel.setLeftToRightOrientation(false);
				WebLookAndFeel.install();

				new ArabicIndexer();
			}
		});
	}
}