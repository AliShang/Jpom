package io.jpom.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.jiangzeyin.common.JsonMessage;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.jpom.common.BaseServerController;
import io.jpom.common.GlobalDefaultExceptionHandler;
import io.jpom.common.JpomManifest;
import io.jpom.common.interceptor.NotLogin;
import io.jpom.model.data.NodeModel;
import io.jpom.model.data.UserModel;
import io.jpom.permission.CacheControllerFeature;
import io.jpom.service.user.RoleService;
import io.jpom.service.user.UserService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 首页
 *
 * @author Administrator
 */
@Controller
@RequestMapping(value = "/")
public class IndexControl extends BaseServerController {
    @Resource
    private UserService userService;

    @Resource
    private RoleService roleService;

    @RequestMapping(value = {"error", "error.html"}, method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    @NotLogin
    public String error(String id) {
        String msg = GlobalDefaultExceptionHandler.getErrorMsg(id);
        setAttribute("msg", msg);
        return "error";
    }

    @RequestMapping(value = "authorize.html", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    @NotLogin
    public String authorize() {
        return "authorize";
    }

    /**
     * 加载首页
     *
     * @return page
     */
    @RequestMapping(value = {"index", "", "index.html", "/"}, method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        if (userService.userListEmpty()) {
            getSession().invalidate();
            return "redirect:install.html";
        }

        // 版本号
        setAttribute("jpomManifest", JpomManifest.getInstance());
        return "index";
    }

    @RequestMapping(value = "menus_data.json", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public String menusData() {
        NodeModel nodeModel = tryGetNode();
        UserModel userModel = getUserModel();
        // 菜单
        InputStream inputStream;
        String secondary;
        if (nodeModel == null) {
            inputStream = ResourceUtil.getStream("classpath:/menus/index.json");
            secondary = "";
        } else {
            inputStream = ResourceUtil.getStream("classpath:/menus/node-index.json");
            secondary = "node/";
        }

        String json = IoUtil.read(inputStream, CharsetUtil.CHARSET_UTF_8);
        JSONArray jsonArray = JSONArray.parseArray(json);
        List<Object> collect1 = jsonArray.stream().filter(o -> {
            JSONObject jsonObject = (JSONObject) o;
            if (!testMenus(jsonObject, userModel, secondary)) {
                return false;
            }
            JSONArray childs = jsonObject.getJSONArray("childs");
            if (childs != null) {
                List<Object> collect = childs.stream().filter(o1 -> {
                    JSONObject jsonObject1 = (JSONObject) o1;
                    return testMenus(jsonObject1, userModel, secondary);
                }).collect(Collectors.toList());
                if (collect.isEmpty()) {
                    return false;
                }
                jsonObject.put("childs", collect);
            }
            return true;
        }).collect(Collectors.toList());
        return JsonMessage.getString(200, "", collect1);
    }

    private boolean testMenus(JSONObject jsonObject, UserModel userModel, String secondary) {
        String url = jsonObject.getString("url");
        if (StrUtil.isNotEmpty(url)) {
            url = FileUtil.normalize(secondary + url);
            url = StrUtil.SLASH + url;
            if (CacheControllerFeature.isSystemUrl(url) && !userModel.isSystemUser()) {
                // 系统管理员权限
                return false;
            }
            CacheControllerFeature.UrlFeature urlFeature = CacheControllerFeature.getUrlFeature(url);
            if (urlFeature != null) {
                // 功能权限
                boolean b = roleService.errorMethodPermission(userModel, urlFeature.getClassFeature(), urlFeature.getMethodFeature());
                if (b) {
                    return false;
                }
            }
        }
        //
        String dynamic = jsonObject.getString("dynamic");
        if (StrUtil.isNotEmpty(dynamic)) {
            if ("showOutGiving".equals(dynamic)) {
                // 是否显示节点分发菜单
                List<NodeModel> list = nodeService.list();
                return list != null && list.size() > 1;
            }
        }
        return true;
    }
}
