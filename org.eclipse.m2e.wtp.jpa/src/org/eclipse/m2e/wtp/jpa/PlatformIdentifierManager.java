/*************************************************************************************
 * Copyright (c) 2012 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Fred Bricon (Red Hat, Inc.) - initial API and implementation
 ************************************************************************************/
package org.eclipse.m2e.wtp.jpa;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jpt.jpa.core.resource.persistence.XmlPersistenceUnit;
import org.eclipse.jpt.jpa.core.resource.persistence.XmlProperty;

/**
 * JPA Platform manager, used to identify a JPA Platform from the content of a persistence.xml
 * 
 * @author Fred Bricon
 */
public class PlatformIdentifierManager {

	private List<IPlatformIdentifier> platformIdentifiers = new ArrayList<>();
	
	public PlatformIdentifierManager() {
		//TODO use extension points
		platformIdentifiers.add(new ReallySimplePlatformIdentifer("hibernate")); //$NON-NLS-1$
		platformIdentifiers.add(new ReallySimplePlatformIdentifer("eclipselink")); //$NON-NLS-1$
	} 
	
	/**
	 * Identifies a JPA Platform from the {@link XmlPersistenceUnit} of a persistence.xml 
	 * by querying all known {@link IPlatformIdentifier}s.
	 * 
	 * @return the detected {@link JpaPlatformDescription} id or null
	 */
	public String identify(XmlPersistenceUnit xmlPersistenceUnit) {
		String platformId = null;
		for (IPlatformIdentifier identifier : platformIdentifiers) {
			platformId = identifier.getPlatformId(xmlPersistenceUnit);
			if (platformId != null) {
				return platformId;
			}
		}
		return null;
	}
	
	private class ReallySimplePlatformIdentifer extends AbstractPlatformIdentifier {
		
		private final String platformName;

		ReallySimplePlatformIdentifer(String platformName) {
			this.platformName = platformName;
		}
		
		@Override
		protected String identifyProvider(String provider) {
			if (provider != null && provider.contains(platformName)) {
				return platformName;
			}
			return null;
		}

		@Override
		protected String identifyProperty(XmlProperty property) {
			return identifyProvider(property.getName());
		}
	}
}
