package com.maknoon;

import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.laf.WebLookAndFeel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class WebLaFTest extends JFrame
{
	int count = 1;
	WebLaFTest()
	{
		DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("Root");
		DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
		WebCheckBoxTree<DefaultMutableTreeNode> tree = new WebCheckBoxTree<>(treeModel);

		JButton add = new JButton("create");
		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				tree.uncheckAll();
				treeRoot.removeAllChildren();
				treeRoot.add(new DefaultMutableTreeNode("Test " + (count++)));
				treeRoot.add(new DefaultMutableTreeNode("Test " + (count++)));
				treeRoot.add(new DefaultMutableTreeNode("Test " + (count++)));
				treeModel.reload();
				tree.getCheckingModel().setChecked(tree.getRootNode(), true);
				//tree.checkAll();
			}
		});

		add(tree, BorderLayout.CENTER);
		add(add, BorderLayout.SOUTH);
		setSize(260, 600);
		setVisible(true);
	}

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				WebLookAndFeel.setLeftToRightOrientation(false);
				WebLookAndFeel.install();
				new WebLaFTest();
			}
		});
	}
}
