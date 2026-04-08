package com.maknoon;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.Properties;
import javax.swing.border.TitledBorder;

class Setting extends JDialog
{
	ArabicIndexer.lang defaultLanguage = ArabicIndexer.language;

	Setting(final ArabicIndexer AI)
	{
		super(AI, true);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);

		final String[] variable = ArabicIndexer.StreamConverter(ArabicIndexer.programFolder + "language/Setting" + ArabicIndexer.language + ".txt");

		setTitle(variable[7]);

		final JPanel languageChoicePanel = new JPanel();
		languageChoicePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), variable[14], TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_JUSTIFICATION, null, Color.red));
		add(languageChoicePanel, BorderLayout.CENTER);

		final JRadioButton arabicLanguageRadioButton = new JRadioButton(variable[15], ArabicIndexer.language == ArabicIndexer.lang.Arabic);
		final JRadioButton englishLanguageRadioButton = new JRadioButton(variable[16], ArabicIndexer.language == ArabicIndexer.lang.English);
		final JRadioButton urduLanguageRadioButton = new JRadioButton(variable[12], ArabicIndexer.language == ArabicIndexer.lang.Urdu);

		final ActionListener languageGroupListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent ae)
			{
				if (ae.getSource() == arabicLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.Arabic;

				if (ae.getSource() == urduLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.Urdu;

				if (ae.getSource() == englishLanguageRadioButton)
					defaultLanguage = ArabicIndexer.lang.English;
			}
		};
		arabicLanguageRadioButton.addActionListener(languageGroupListener);
		urduLanguageRadioButton.addActionListener(languageGroupListener);
		englishLanguageRadioButton.addActionListener(languageGroupListener);

		languageChoicePanel.add(new JLabel("                "));
		languageChoicePanel.add(arabicLanguageRadioButton);
		languageChoicePanel.add(new JLabel("           "));
		languageChoicePanel.add(urduLanguageRadioButton);
		languageChoicePanel.add(new JLabel("           "));
		languageChoicePanel.add(englishLanguageRadioButton);
		languageChoicePanel.add(new JLabel("                "));

		final ButtonGroup languageGroup = new ButtonGroup();
		languageGroup.add(arabicLanguageRadioButton);
		languageGroup.add(englishLanguageRadioButton);
		languageGroup.add(urduLanguageRadioButton);

		final JPanel closePanel = new JPanel();
		closePanel.setLayout(new BorderLayout());
		add(closePanel, BorderLayout.SOUTH);

		final JButton cancelButton = new JButton(variable[30], new ImageIcon(ArabicIndexer.programFolder + "images/cancel.png"));
		cancelButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});

		final JButton closeButton = new JButton(variable[31], new ImageIcon(ArabicIndexer.programFolder + "images/ok.png"));
		closeButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					// Update version 2.1
					final Properties prop = new Properties();
					prop.load(new FileInputStream(ArabicIndexer.programFolder + "setting/setting.properties"));
					prop.setProperty("language", String.valueOf(defaultLanguage));
					prop.store(new OutputStreamWriter(new FileOutputStream(ArabicIndexer.programFolder + "setting/setting.properties")), null);
				}
				catch (IOException ae)
				{
					ae.printStackTrace();
				}

				/*
				 * Make it last to not shown the dialog of autorun configuration
				 * after doing the switching in the node or the language
				 */
				// if there is a change in the setting
				if (ArabicIndexer.language != defaultLanguage)
				{
					if (JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), variable[34], variable[4], JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{variable[35], variable[36]}, variable[35]))
					{
						AI.pre_shutdown(); // Version 1.8

						try
						{
							if (ArabicIndexer.isWindows)
								Runtime.getRuntime().exec(new String[]{new File(ArabicIndexer.programFolder + "launcher.bat").getAbsolutePath()}); // Version 1.7, Launcher wait for 0.7 second then start again. it gives some time for shutdown this class
							else
								Runtime.getRuntime().exec(new String[]{new File(ArabicIndexer.programFolder + "launcher.sh").getAbsolutePath()}); // To allow Dock icon and the arabic title name in MacOS
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
						System.exit(0);
					}
				}
				dispose();
			}
		});

		final JPanel closePanelWest = new JPanel();
		closePanelWest.add(closeButton);
		closePanelWest.add(cancelButton);
		closePanel.add(closePanelWest, BorderLayout.WEST);

		if (ArabicIndexer.language == ArabicIndexer.lang.English)
		{
			arabicLanguageRadioButton.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
			urduLanguageRadioButton.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
		}
		else
		{
			getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
			englishLanguageRadioButton.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		}

		// To locate the JDialog at the center of the screen
		pack();
		ArabicIndexer.centerInScreen(this);
		setVisible(true);
	}
}