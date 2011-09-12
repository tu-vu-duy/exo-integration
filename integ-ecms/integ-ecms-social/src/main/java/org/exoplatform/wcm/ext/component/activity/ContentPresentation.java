/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wcm.ext.component.activity;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;

import org.exoplatform.download.DownloadService;
import org.exoplatform.download.InputStreamDownloadResource;
import org.exoplatform.ecm.resolver.JCRResourceResolver;
import org.exoplatform.ecm.webui.presentation.UIBaseNodePresentation;
import org.exoplatform.resolver.ResourceResolver;
import org.exoplatform.services.cms.impl.DMSConfiguration;
import org.exoplatform.services.cms.mimetype.DMSMimeTypeResolver;
import org.exoplatform.services.cms.templates.TemplateService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.wcm.webui.Utils;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.lifecycle.Lifecycle;
import org.exoplatform.webui.ext.UIExtension;
import org.exoplatform.webui.ext.UIExtensionManager;

/**
 * Created by The eXo Platform SAS
 * 
 * @author hai_lethanh Mar 23, 2011 This class used to render the media contents
 */
@ComponentConfig(lifecycle = Lifecycle.class)
public class ContentPresentation extends UIBaseNodePresentation {

  /** The node that want to view its content */
  private Node                node;

  /** The resource resolver. */
  private JCRResourceResolver resourceResolver;

  /** The repository. */
  private String              repository;

  public ContentPresentation() {
  }

  public void setNode(Node node) {
    this.node = node;
  }

  @Override
  public Node getNode() throws Exception {
    return node;
  }

  @Override
  public Node getOriginalNode() throws Exception {
    return node;
  }

  public String getDownloadLink(Node node) throws Exception {
    DownloadService dservice = getApplicationComponent(DownloadService.class);
    Node jcrContentNode = node.getNode(org.exoplatform.ecm.webui.utils.Utils.JCR_CONTENT);
    InputStream input = jcrContentNode.getProperty(org.exoplatform.ecm.webui.utils.Utils.JCR_DATA).getStream();
    String mimeType = jcrContentNode.getProperty(org.exoplatform.ecm.webui.utils.Utils.JCR_MIMETYPE).getString();
    InputStreamDownloadResource dresource = new InputStreamDownloadResource(input, mimeType);
    DMSMimeTypeResolver mimeTypeSolver = DMSMimeTypeResolver.getInstance();
    String ext = mimeTypeSolver.getExtension(mimeType);
    String fileName = node.getName();
    if (fileName.lastIndexOf("." + ext) < 0) {
      fileName = fileName + "." + ext;
    }
    dresource.setDownloadName(fileName);
    return dservice.getDownloadLink(dservice.addDownloadResource(dresource));
  }

  @Override
  public String getTemplatePath() throws Exception {
    TemplateService templateService = getApplicationComponent(TemplateService.class);
    return templateService.getTemplatePath(getOriginalNode(), false);
  }

  public String getTemplate() {
    try {
      return getTemplatePath();
    } catch (Exception e) {
      return null;
    }
  }

  /*
   * (non-Javadoc)
   * @see
   * org.exoplatform.webui.core.UIComponent#getTemplateResourceResolver(org.
   * exoplatform.webui.application.WebuiRequestContext, java.lang.String)
   */
  public ResourceResolver getTemplateResourceResolver(WebuiRequestContext context, String template) {
    try {
      DMSConfiguration dmsConfiguration = getApplicationComponent(DMSConfiguration.class);
      String workspace = dmsConfiguration.getConfig().getSystemWorkspace();
      resourceResolver = new JCRResourceResolver(repository, workspace, "exo:templateFile");
    } catch (Exception e) {
      Utils.createPopupMessage(this, "UIMessageBoard.msg.get-template-resource", null, ApplicationMessage.ERROR);
    }
    return resourceResolver;
  }

  /*
   * (non-Javadoc)
   * @see org.exoplatform.ecm.webui.presentation.NodePresentation#getNodeType()
   */
  public String getNodeType() throws Exception {
    return getOriginalNode().getPrimaryNodeType().getName();
  }

  /*
   * (non-Javadoc)
   * @see
   * org.exoplatform.ecm.webui.presentation.NodePresentation#isNodeTypeSupported
   * ()
   */
  public boolean isNodeTypeSupported() {
    return false;
  }

  public UIComponent getUIComponent(String mimeType) throws Exception {
    if (mimeType != null && !mimeType.startsWith("text") && !mimeType.startsWith("application")) {
      UIExtensionManager manager = getApplicationComponent(UIExtensionManager.class);
      List<UIExtension> extensions = manager.getUIExtensions(org.exoplatform.ecm.webui.utils.Utils.FILE_VIEWER_EXTENSION_TYPE);
      Map<String, Object> context = new HashMap<String, Object>();
      context.put(org.exoplatform.ecm.webui.utils.Utils.MIME_TYPE, mimeType);
      for (UIExtension extension : extensions) {
        UIComponent uiComponent = manager.addUIExtension(extension, context, this);
        if (uiComponent != null)
          return uiComponent;
      }
    }

    return null;
  }

  @Override
  public UIComponent getCommentComponent() throws Exception {
    return null;
  }

  @Override
  public UIComponent getRemoveAttach() throws Exception {
    return null;
  }

  @Override
  public UIComponent getRemoveComment() throws Exception {
    return null;
  }

  @Override
  public String getRepositoryName() throws Exception {
    return ((ManageableRepository) getNode().getSession().getRepository()).getConfiguration().getName();
  }
}
