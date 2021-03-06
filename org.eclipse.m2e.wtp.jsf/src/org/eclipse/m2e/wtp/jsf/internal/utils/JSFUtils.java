/*******************************************************************************
 * Copyright (c) 2012 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Fred Bricon (Red Hat, Inc.) - initial API and implementation
 ******************************************************************************/

package org.eclipse.m2e.wtp.jsf.internal.utils;

import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_FACET;
import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_VERSION_1_1;
import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_VERSION_1_2;
import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_VERSION_2_0;
import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_VERSION_2_1;
import static org.eclipse.m2e.wtp.jsf.internal.MavenJSFConstants.JSF_VERSION_2_2;

import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.codehaus.plexus.util.IOUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.m2e.wtp.ProjectUtils;
import org.eclipse.m2e.wtp.jsf.internal.Messages;
import org.eclipse.m2e.wtp.jsf.internal.utils.xpl.JSFAppConfigUtils;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class JSFUtils {

	private static final Logger LOG = LoggerFactory.getLogger(JSFUtils.class);

	public static final String FACES_SERVLET = "javax.faces.webapp.FacesServlet"; //$NON-NLS-1$

	private static final String FACES_SERVLET_XPATH = "//servlet[servlet-class=\"" + FACES_SERVLET + "\"]"; //$NON-NLS-1$ //$NON-NLS-2$

	private JSFUtils() {
		// no public constructor
	}

	/**
	 * Return the faces-config.xml of the given project, or null if faces-config.xml doesn't exist
	 */
	public static IFile getFacesconfig(IProject project) {
		IFile facesConfig = null;
		@SuppressWarnings("unchecked")
		List<String> configFiles = JSFAppConfigUtils.getConfigFilesFromContextParam(project);
		for (String configFile : configFiles) {
			facesConfig = ProjectUtils.getWebResourceFile(project, configFile);
			if (facesConfig != null && facesConfig.exists()) {
				return facesConfig;
			}
		}
		facesConfig = ProjectUtils.getWebResourceFile(project, "WEB-INF/faces-config.xml"); //$NON-NLS-1$
		
		return facesConfig;
	}
	
	/**
	 * Return the faces config version of the given project, or null if faces-config.xml doesn't exist
	 */
	public static  String getVersionFromFacesconfig(IProject project) {
		IFile facesConfig = getFacesconfig(project);
	    String version = null;
		if (facesConfig != null) {
			InputStream in = null;
			try {
				facesConfig.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
				in = facesConfig.getContents();
				FacesConfigQuickPeek peek = new FacesConfigQuickPeek(in);
				version = peek.getVersion();
			} catch (CoreException e) {
				// ignore
				LOG.error(Messages.JSFUtils_Error_Reading_FacesConfig, e);
			} finally {
				IOUtil.close(in);
			}
		}
		return version;
	}
	
	/**
	 * Checks if the webXml {@link IFile} declares the Faces servlet.
	 */
	public static boolean hasFacesServlet(IFile webXml) {
		//We look for javax.faces.webapp.FacesServlet in web.xml
		if (webXml == null || !webXml.isAccessible()) {
			return false;
		}
		
		InputStream is = null;
		try {
			is = webXml.getContents();
			return hasFacesServlet(is);
		} catch (Exception e) {
			LOG.error(NLS.bind(Messages.JSFUtils_Error_Finding_Faces_Servlet_In_WebXml, FACES_SERVLET, webXml.getLocation().toOSString()), e);
		} finally {
			IOUtil.close(is);
		}
		return false;
	}	
	
	/**
	 * Checks if the webXml {@link InputStream} declares the Faces servlet.
	 */
	public static boolean hasFacesServlet(InputStream input) {
		if (input == null) {
			return false;
		}
		boolean hasFacesServlet = false;
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(false); // never forget this!
			domFactory.setValidating(false);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			
			//FIX_POST_MIGRATION builder.setEntityResolver(new DtdResolver());
			Document doc = builder.parse(input);

			XPath xpath = XPathFactory.newInstance().newXPath();
			XPathExpression expr = xpath.compile(FACES_SERVLET_XPATH);
			hasFacesServlet = null != expr.evaluate(doc, XPathConstants.NODE);
		} catch (Exception e) {
			LOG.error(NLS.bind(Messages.JSFUtils_Error_Finding_Faces_Servlet,FACES_SERVLET), e);
		}
		return hasFacesServlet;
	}		
	
	/**
	 * Determines the JSF version by searching for the methods of javax.faces.application.Application 
	 * in the project's classpath.
	 * @param project : the java project to analyze
	 * @return the JSF version (1.1, 1.2, 2.0, 2.1, 2.2) found in the classpath, 
	 * or null if the project doesn't depend on JSF 
	 */
	public static String getJSFVersionFromClasspath(IProject project) {
		String version = null;
		IJavaProject javaProject = JavaCore.create(project);
		if (javaProject != null) {
			IType type = null;
			try {
				type = javaProject.findType("javax.faces.context.FacesContext");//$NON-NLS-1$ 
			} catch (JavaModelException e) {
				LOG.error(Messages.JSFUtils_Error_Searching_For_JSF_Type, e) ;
			}
			if (type != null) {
				String[] emptyParams = new String[0];
				if (type.getMethod("getResourceLibraryContracts", emptyParams).exists()) { //$NON-NLS-1$
					return JSF_VERSION_2_2;
				}
				if (type.getMethod("isReleased", emptyParams).exists()) { //$NON-NLS-1$
					return JSF_VERSION_2_1;					
				}
				if (type.getMethod("getAttributes", emptyParams).exists() &&     //$NON-NLS-1$
					type.getMethod("getPartialViewContext", emptyParams).exists()) {       //$NON-NLS-1$
					return JSF_VERSION_2_0;
			    }
				if (type.getMethod("getELContext", emptyParams).exists()) {  //$NON-NLS-1$
					return JSF_VERSION_1_2;
				} 
				version = JSF_VERSION_1_1;
			}
		}
		return version;
	}
	
	/**
	 * Transforms a JSF version string into the equivalent {@link IProjectFacetVersion}.
	 * If no equivalent {@link IProjectFacetVersion} is available, it's assumed the version 
	 * is not supported and the latest available JSF version is returned.
	 *  
	 * @param version
	 * @return
	 * @since 0.18.0
	 */
	public static IProjectFacetVersion getSafeJSFFacetVersion(String version) {
		IProjectFacetVersion facetVersion = null;
		if (version != null && version.trim().length() > 0) {
			try {
				facetVersion = JSF_FACET.getVersion(version);
			} catch (Exception e) {
				LOG.error(NLS.bind(Messages.JSFUtils_Error_Finding_JSF_Version,version), e);
				try {
					//We assume the detected version is not supported *yet* so take the latest.
					facetVersion = JSF_FACET.getLatestVersion();
				} catch(CoreException cex) {
					LOG.error(Messages.JSFUtils_Error_Finding_Latest_JSF_Version, cex);
					facetVersion =  JSF_FACET.getDefaultVersion();		
				}
			}
		}
		return facetVersion;
	}
}
