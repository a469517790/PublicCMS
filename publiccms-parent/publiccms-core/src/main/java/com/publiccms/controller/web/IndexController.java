package com.publiccms.controller.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.view.UrlBasedViewResolver;
import org.springframework.web.util.UrlPathHelper;

import com.publiccms.common.api.Config;
import com.publiccms.common.constants.CommonConstants;
import com.publiccms.common.tools.CommonUtils;
import com.publiccms.common.tools.ControllerUtils;
import com.publiccms.common.tools.RequestUtils;
import com.publiccms.entities.cms.CmsCategory;
import com.publiccms.entities.cms.CmsContent;
import com.publiccms.entities.sys.SysDomain;
import com.publiccms.entities.sys.SysSite;
import com.publiccms.entities.sys.SysUser;
import com.publiccms.logic.component.config.ConfigComponent;
import com.publiccms.logic.component.config.LoginConfigComponent;
import com.publiccms.logic.component.site.SiteComponent;
import com.publiccms.logic.component.site.StatisticsComponent;
import com.publiccms.logic.component.template.MetadataComponent;
import com.publiccms.logic.component.template.TemplateCacheComponent;
import com.publiccms.logic.component.template.TemplateComponent;
import com.publiccms.logic.service.cms.CmsCategoryService;
import com.publiccms.logic.service.cms.CmsContentService;
import com.publiccms.logic.service.sys.SysUserService;
import com.publiccms.views.pojo.entities.CmsPageData;
import com.publiccms.views.pojo.entities.CmsPageMetadata;
import com.publiccms.views.pojo.entities.ParameterType;

/**
 * 
 * IndexController 统一分发Controller
 *
 */
@Controller
public class IndexController {
    @Autowired
    private MetadataComponent metadataComponent;
    @Autowired
    private TemplateComponent templateComponent;
    @Autowired
    private TemplateCacheComponent templateCacheComponent;
    @Autowired
    private ConfigComponent configComponent;
    @Autowired
    private LocaleResolver localeResolver;
    @Autowired
    protected SiteComponent siteComponent;
    @Autowired
    private CmsContentService contentService;
    @Autowired
    private CmsCategoryService categoryService;
    @Autowired
    private SysUserService userService;
    @Autowired
    private StatisticsComponent statisticsComponent;

    private UrlPathHelper urlPathHelper = new UrlPathHelper();

    /**
     * REST页面请求统一分发
     * 
     * @param site
     * @param id
     * @param body
     * @param request
     * @param response
     * @param model
     * @return view name
     */
    @RequestMapping({ "/**/{id:[0-9]+}" })
    public String rest(@RequestAttribute SysSite site, @PathVariable("id") long id, @RequestBody(required = false) String body,
            HttpServletRequest request, HttpServletResponse response, ModelMap model) {
        return restPage(site, id, null, body, request, response, model);
    }

    /**
     * REST页面请求统一分发
     * 
     * @param site
     * @param id
     * @param pageIndex
     * @param body
     * @param request
     * @param response
     * @param model
     * @return view name
     */
    @RequestMapping({ "/**/{id:[0-9]+}_{pageIndex:[0-9]+}" })
    public String restPage(@RequestAttribute SysSite site, @PathVariable("id") long id,
            @PathVariable("pageIndex") Integer pageIndex, @RequestBody(required = false) String body, HttpServletRequest request,
            HttpServletResponse response, ModelMap model) {
        String requestPath = urlPathHelper.getLookupPathForRequest(request);
        if (requestPath.endsWith(CommonConstants.SEPARATOR)) {
            requestPath = requestPath.substring(0, requestPath.lastIndexOf(CommonConstants.SEPARATOR, requestPath.length() - 2))
                    + CommonConstants.getDefaultSubfix();
        } else {
            requestPath = requestPath.substring(0, requestPath.lastIndexOf(CommonConstants.SEPARATOR))
                    + CommonConstants.getDefaultSubfix();
        }
        return getViewName(site, id, pageIndex, requestPath, body, request, response, model);
    }

    /**
     * 页面请求统一分发
     * 
     * @param site
     * @param body
     * @param request
     * @param response
     * @param model
     * @return view name
     */
    @RequestMapping({ CommonConstants.SEPARATOR, "/**" })
    public String page(@RequestAttribute SysSite site, @RequestBody(required = false) String body, HttpServletRequest request,
            HttpServletResponse response, ModelMap model) {
        String requestPath = urlPathHelper.getLookupPathForRequest(request);
        if (requestPath.endsWith(CommonConstants.SEPARATOR)) {
            requestPath += CommonConstants.getDefaultPage();
        }
        return getViewName(site, null, null, requestPath, body, request, response, model);
    }

    private String getViewName(SysSite site, Long id, Integer pageIndex, String requestPath, String body,
            HttpServletRequest request, HttpServletResponse response, ModelMap model) {
        SysDomain domain = siteComponent.getDomain(request.getServerName());
        String fullRequestPath = siteComponent.getViewName(site, domain, requestPath);
        String templatePath = siteComponent.getWebTemplateFilePath() + fullRequestPath;
        CmsPageMetadata metadata = metadataComponent.getTemplateMetadata(templatePath);
        if (metadata.isUseDynamic()) {
            if (metadata.isNeedLogin() && null == ControllerUtils.getUserFromSession(request.getSession())) {
                Map<String, String> config = configComponent.getConfigData(site.getId(), Config.CONFIG_CODE_SITE);
                String loginPath = config.get(LoginConfigComponent.CONFIG_LOGIN_PATH);
                StringBuilder sb = new StringBuilder(UrlBasedViewResolver.REDIRECT_URL_PREFIX);
                if (CommonUtils.notEmpty(loginPath)) {
                    return sb.append(loginPath).append("?returnUrl=")
                            .append(RequestUtils.getEncodePath(requestPath, request.getQueryString())).toString();
                } else {
                    return sb.append(site.getDynamicPath()).toString();
                }
            }
            String[] acceptParameters = StringUtils.split(metadata.getAcceptParameters(), CommonConstants.COMMA_DELIMITED);
            if (CommonUtils.notEmpty(acceptParameters)) {
                if (!billingRequestParametersToModel(request, acceptParameters, metadata.getParameterTypeMap(), site, model)) {
                    try {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    } catch (IOException e) {
                    }
                    return requestPath;
                }
                if (null != id && ArrayUtils.contains(acceptParameters, "id")) {
                    model.addAttribute("id", id.toString());
                    if (null != pageIndex && ArrayUtils.contains(acceptParameters, "pageIndex")) {
                        model.addAttribute("pageIndex", pageIndex.toString());
                    }
                }
            }
            CmsPageData data = metadataComponent.getTemplateData(
                    siteComponent.getWebTemplateFilePath() + siteComponent.getCurrentViewNamePrefix(site, domain) + requestPath);
            model.addAttribute("metadata", metadata.getAsMap(data));
            if (metadata.isNeedBody()) {
                model.addAttribute("body", body);
            }
            if (CommonUtils.notEmpty(metadata.getContentType())) {
                response.setContentType(metadata.getContentType());
            }
            if (CommonUtils.notEmpty(metadata.getCacheTime()) && 0 < metadata.getCacheTime()) {
                int cacheMillisTime = metadata.getCacheTime() * 1000;
                String cacheControl = request.getHeader("Cache-Control");
                String pragma = request.getHeader("Pragma");
                if (CommonUtils.notEmpty(cacheControl) && "no-cache".equalsIgnoreCase(cacheControl)
                        || CommonUtils.notEmpty(pragma) && "no-cache".equalsIgnoreCase(pragma)) {
                    cacheMillisTime = 0;
                }
                return templateCacheComponent.getCachedPath(requestPath, fullRequestPath, localeResolver.resolveLocale(request),
                        cacheMillisTime, acceptParameters, request, model);
            }
        } else {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException e) {
            }
        }
        return requestPath;
    }

    private boolean billingRequestParametersToModel(HttpServletRequest request, String[] acceptParameters,
            Map<String, ParameterType> parameterTypeMap, SysSite site, ModelMap model) {
        for (String parameterName : acceptParameters) {
            ParameterType parameterType = null;
            if (null != parameterTypeMap) {
                parameterType = parameterTypeMap.get(parameterName);
            }
            String[] values = request.getParameterValues(parameterName);
            if (null == parameterType) {
                billingValue(parameterName, request.getParameterValues(parameterName), model);
            } else if (!parameterType.isRequired() || CommonUtils.notEmpty(values)) {
                if (!billingValue(parameterName, values, parameterType, site, model)) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void billingValue(String parameterName, String[] values, ModelMap model) {
        if (CommonUtils.notEmpty(values)) {
            if (1 < values.length) {
                RequestUtils.removeCRLF(values);
                model.addAttribute(parameterName, values);
            } else {
                model.addAttribute(parameterName, RequestUtils.removeCRLF(values[0]));
            }
        }
    }

    private boolean billingValue(String parameterName, String[] values, ParameterType parameterType, SysSite site,
            ModelMap model) {
        if (CommonUtils.notEmpty(parameterType.getAlias())) {
            parameterName = parameterType.getAlias();
        }
        switch (parameterType.getType()) {
        case Config.INPUTTYPE_TEXTAREA:
            if (parameterType.isArray()) {
                model.addAttribute(parameterName, values);
            } else {
                model.addAttribute(parameterName, values[0]);
            }
            break;
        case Config.INPUTTYPE_NUMBER:
            if (parameterType.isArray()) {
                Set<Long> set = new TreeSet<>();
                for (String s : values) {
                    try {
                        set.add(Long.valueOf(s));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                model.addAttribute(parameterName, set.toArray(new Long[set.size()]));
            } else if (CommonUtils.notEmpty(values)) {
                try {
                    model.addAttribute(parameterName, Long.valueOf(values[0]));
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (parameterType.isRequired()) {
                return false;
            }
            break;
        case Config.INPUTTYPE_CONTENT:
            if (parameterType.isArray()) {
                Set<Long> set = new TreeSet<>();
                for (String s : values) {
                    try {
                        set.add(Long.valueOf(s));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                List<CmsContent> entityList = contentService.getEntitys(set.toArray(new Long[set.size()]));
                entityList.forEach(e -> {
                    Integer clicks = statisticsComponent.getContentClicks(e.getId());
                    if (null != clicks) {
                        e.setClicks(e.getClicks() + clicks);
                    }
                    templateComponent.initContentUrl(site, e);
                    templateComponent.initContentCover(site, e);
                });
                model.addAttribute(parameterName, entityList);
            } else if (CommonUtils.notEmpty(values)) {
                try {
                    CmsContent entity = contentService.getEntity(Long.valueOf(values[0]));
                    if ((null == entity || entity.isDisabled()) && parameterType.isRequired()) {
                        return false;
                    }
                    Integer clicks = statisticsComponent.getContentClicks(entity.getId());
                    if (null != clicks) {
                        entity.setClicks(entity.getClicks() + clicks);
                    }
                    templateComponent.initContentUrl(site, entity);
                    model.addAttribute(parameterName, entity);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (parameterType.isRequired()) {
                return false;
            }
            break;
        case Config.INPUTTYPE_CATEGORY:
            if (parameterType.isArray()) {
                Set<Integer> set = new TreeSet<>();
                for (String s : values) {
                    try {
                        set.add(Integer.valueOf(s));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                List<CmsCategory> entityList = categoryService.getEntitys(set.toArray(new Integer[set.size()]));
                entityList.forEach(e -> {
                    templateComponent.initCategoryUrl(site, e);
                });
                model.addAttribute(parameterName, entityList);
            } else if (CommonUtils.notEmpty(values)) {
                try {
                    CmsCategory entity = categoryService.getEntity(Integer.valueOf(values[0]));
                    if ((null == entity || entity.isDisabled()) && parameterType.isRequired()) {
                        return false;
                    }
                    templateComponent.initCategoryUrl(site, entity);
                    model.addAttribute(parameterName, entity);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (parameterType.isRequired()) {
                return false;
            }
            break;
        case Config.INPUTTYPE_USER:
            if (parameterType.isArray()) {
                Set<Long> set = new TreeSet<>();
                for (String s : values) {
                    try {
                        set.add(Long.valueOf(s));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                model.addAttribute(parameterName, userService.getEntitys(set.toArray(new Long[set.size()])));
            } else if (CommonUtils.notEmpty(values)) {
                try {
                    SysUser entity = userService.getEntity(Long.valueOf(values[0]));
                    if ((null == entity || entity.isDisabled()) && parameterType.isRequired()) {
                        return false;
                    }
                    model.addAttribute(parameterName, entity);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (parameterType.isRequired()) {
                return false;
            }
            break;
        default:
            if (parameterType.isArray()) {
                RequestUtils.removeCRLF(values);
                model.addAttribute(parameterName, values);
            } else {
                model.addAttribute(parameterName, RequestUtils.removeCRLF(values[0]));
            }
        }

        return true;
    }

}
