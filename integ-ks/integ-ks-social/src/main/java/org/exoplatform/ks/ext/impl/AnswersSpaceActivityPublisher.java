/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ks.ext.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.faq.service.Answer;
import org.exoplatform.faq.service.Comment;
import org.exoplatform.faq.service.FAQNodeTypes;
import org.exoplatform.faq.service.FAQService;
import org.exoplatform.faq.service.Question;
import org.exoplatform.faq.service.Utils;
import org.exoplatform.faq.service.impl.AnswerEventListener;
import org.exoplatform.ks.common.TransformHTML;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * @author <a href="mailto:patrice.lamarque@exoplatform.com">Patrice
 *         Lamarque</a>
 * @version $Revision$
 */
public class AnswersSpaceActivityPublisher extends AnswerEventListener {

  public static final String SPACE_APP_ID = "ks-answer:spaces";
  
  public static final String QUESTION_ID_KEY = "QuestionId";
  public static final String ANSWER_ID_KEY = "AnswerId";
  public static final String COMMENT_ID_KEY = "CommentId";
  public static final String ACTIVITY_TYPE_KEY = "ActivityType";
  public static final String AUTHOR_KEY = "Author";
  public static final String LINK_KEY = "Link";
  public static final String QUESTION_NAME_KEY = "Name";
  public static final String LANGUAGE_KEY = "Language";
  public static final String ANSWER = "Answer";
  public static final String QUESTION = "Question";
  public static final String COMMENT = "Comment";
  public static final String ANSWER_ADD = ANSWER + "Add";
  public static final String QUESTION_ADD = QUESTION + "Add";
  public static final String COMMENT_ADD = COMMENT + "Add";
  public static final String ANSWER_UPDATE = ANSWER + "Update";
  public static final String QUESTION_UPDATE = QUESTION + "Update";
  public static final String COMMENT_UPDATE = COMMENT + "Update";
  
  private static Log LOG = ExoLogger.getExoLogger(AnswerEventListener.class);
  
  private boolean isCategoryPublic(String categoryId, List<String> categories) throws Exception {
    if (categoryId != null) {
      FAQService faqS = (FAQService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(FAQService.class);
      String[] users = (String[]) faqS.readCategoryProperty(categoryId, FAQNodeTypes.EXO_USER_PRIVATE, String[].class);
      int parentIndex = categories.indexOf(categoryId) - 1; 
        
      return org.exoplatform.forum.service.Utils.isEmpty(users) && (parentIndex < 0 ? true : isCategoryPublic(categories.get(parentIndex), categories));
    }
    return false;
  }
  
  private boolean isQuestionPublic(Question question) {
    // the question is public if it is not activated or approved
    return question != null && question.isActivated() && question.isApproved();
  }
  
  private boolean isAnswerPublic(Answer answer) {
    // the answer is public if it is not activated or approved
    return answer != null && answer.getApprovedAnswers() && answer.getActivateAnswers();
  }
  
  private Identity getSpaceIdentity(String categoryId) {
    if (categoryId.indexOf(Utils.CATE_SPACE_ID_PREFIX) < 0) 
      return null;
    String prettyname = categoryId.split(Utils.CATE_SPACE_ID_PREFIX)[1];
    IdentityManager identityM = (IdentityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(IdentityManager.class);
    SpaceService spaceService  = (SpaceService) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(SpaceService.class);
    Space space = spaceService.getSpaceByPrettyName(prettyname);
    if (space != null)
      return identityM.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    else return null;
  }
  
  private ExoSocialActivity newActivity(Identity author, String title, String body, Map<String, String> templateParams) {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setUserId(author.getId());
    activity.setTitle(StringEscapeUtils.unescapeHtml(title));
    activity.setBody(StringEscapeUtils.unescapeHtml(TransformHTML.cleanHtmlCode(body, (List<String>) Collections.EMPTY_LIST)));
    activity.setType(SPACE_APP_ID);
    activity.setTemplateParams(templateParams);
    return activity;
  }
  
  private Map<String, String> updateTemplateParams(Map<String, String> templateParams, String activityType, String questionId, String questionName, String language, String link) {
    templateParams.put(QUESTION_ID_KEY, questionId);
    templateParams.put(ACTIVITY_TYPE_KEY, activityType);
    templateParams.put(QUESTION_NAME_KEY, questionName);
    templateParams.put(LINK_KEY, link);
    templateParams.put(LANGUAGE_KEY, language);
    return templateParams;
  }
  
  @Override
  public void saveAnswer(String questionId, Answer answer, boolean isNew) {
    try {
      ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
      IdentityManager identityM = (IdentityManager) exoContainer.getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
      FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
      if (isAnswerPublic(answer)) {
        Question q = faqS.getQuestionById(questionId);
        if (isQuestionPublic(q)) {
          Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, answer.getResponseBy(), false);
          Identity streamOwner = null, author = userIdentity;
          String catId = q.getCategoryId();
          Identity spaceIdentity = getSpaceIdentity(catId);
          if (spaceIdentity != null) {
            // publish the activity in the space stream.
            streamOwner = spaceIdentity;
          }
          List<String> categoryIds = faqS.getCategoryPath(catId);
          Collections.reverse(categoryIds);
          if (streamOwner == null && isCategoryPublic(catId, categoryIds)) {
            // publish the activity in the user stream.
            streamOwner = userIdentity;
          }
          String activityType = isNew ? ANSWER_ADD : ANSWER_UPDATE;
          if (streamOwner != null) {
            Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), activityType, questionId, q.getQuestion(), q.getLanguage(), q.getLink());
            templateParams.put(ANSWER_ID_KEY, answer.getId());
            templateParams.put(AUTHOR_KEY, answer.getResponseBy());
            activityM.saveActivityNoReturn(streamOwner, newActivity(author, "@" + answer.getResponseBy(), answer.getResponses(), templateParams));
          }
        }
      }
    }  catch (Exception e) {
      LOG.error("Can not record Activity for space when post answer ", e);
    }

  }

  @Override
  public void saveComment(String questionId, Comment comment, boolean isNew) {}

  @Override
  public void saveQuestion(Question question, boolean isNew) {
    try {
      if (isQuestionPublic(question)) {
        ExoContainer exoContainer = ExoContainerContext.getCurrentContainer();
        IdentityManager identityM = (IdentityManager) exoContainer.getComponentInstanceOfType(IdentityManager.class);
        ActivityManager activityM = (ActivityManager) exoContainer.getComponentInstanceOfType(ActivityManager.class);
        FAQService faqS = (FAQService) exoContainer.getComponentInstanceOfType(FAQService.class);
        Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, question.getAuthor(), false);
        Identity streamOwner = null, author = userIdentity;
        String catId = (String) faqS.readQuestionProperty(question.getId(), FAQNodeTypes.EXO_CATEGORY_ID, String.class);
        Identity spaceIdentity = getSpaceIdentity(catId);
        if (spaceIdentity != null) {
          // publish the activity in the space stream.
          streamOwner = spaceIdentity;
        }
        List<String> categoryIds = faqS.getCategoryPath(catId);
        Collections.reverse(categoryIds);
        if (streamOwner == null && isCategoryPublic(catId, categoryIds)) {
          streamOwner = userIdentity;
        }
        if (streamOwner != null) {
          Map<String, String> templateParams = updateTemplateParams(new HashMap<String, String>(), isNew ? QUESTION_ADD : QUESTION_UPDATE, question.getId(), question.getQuestion(), question.getLanguage(), question.getLink());
          activityM.saveActivityNoReturn(streamOwner, newActivity(author, "@" + question.getAuthor(), question.getDetail(), templateParams));
        }
      }
      
      
    } catch (Exception e) {
      LOG.error("Can not record Activity for space when add new question ", e);
    }
  }

  @Override
  public void saveAnswer(String questionId, Answer[] answers, boolean isNew) {
    try {
      Class.forName("org.exoplatform.social.core.manager.IdentityManager");
      if (answers != null) {
        for (Answer a : answers) {
          saveAnswer(questionId, a, isNew);
        }
      }
      
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled())
        LOG.debug("Please check the integrated project does the social deploy? " + e.getMessage());
    } catch (Exception e) {
      LOG.error("Can not record Activity for space when post answer " + e.getMessage());
    }
  }

}
