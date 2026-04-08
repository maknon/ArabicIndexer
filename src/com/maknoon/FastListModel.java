package com.maknoon;

import javax.swing.*;
import java.util.*;

// This class is used instead of the DefaultListModel for performance.
class FastListModel extends AbstractListModel<String>
{
    private final List<String> model;

    public FastListModel(final List<String> l)
    {
        model = new ArrayList<>(l);
    }

    public int getSize()
    {
        return model.size();
    }

    public String getElementAt(int index)
    {
        return model.get(index);
    }

    /*
    public void addElement(String element)
    {
        if(model.add(element))
            fireContentsChanged(this, 0, getSize());

        fireIntervalAdded(element, 0, getSize());
    }

    public void addAll(List<String> elements)
    {
        model.addAll(elements);
        fireContentsChanged(this, 0, getSize());
    }
    */
}