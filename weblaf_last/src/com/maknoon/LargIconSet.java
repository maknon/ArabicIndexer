package com.maknoon;

import com.alee.api.resource.ClassResource;
import com.alee.managers.icon.set.XmlIconSet;

public final class LargIconSet extends XmlIconSet
{
	public LargIconSet ()
	{
		super ( new ClassResource( LargIconSet.class, "../../setting/large-icon-set.xml") );
	}
}