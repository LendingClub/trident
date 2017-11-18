package org.lendingclub.trident.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapidoid.u.U;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

@Controller
@PermitAll
@RequestMapping("/")
public class CLIDownloadController {

    Logger logger = LoggerFactory.getLogger(CLIDownloadController.class);

    @Autowired
    ApplicationContext applicationContext;


    @RequestMapping("/cli")
    public ModelAndView cli(HttpServletRequest request) {

        String url = request.getRequestURL().toString().toLowerCase();

        url = url.substring(0, url.indexOf("/cli"));

        url = url + "/cli/install";

        Map<String,String> m = U.map("curlBashUrl", url);

        return new ModelAndView("cli", m);
    }

    @RequestMapping(path = "/cli/install", produces = "text/plain")
    public void installClient(HttpServletRequest request, HttpServletResponse response) throws IOException {
        org.springframework.core.io.Resource resource = applicationContext.getResource("classpath:cli/install-cli.sh");

        try (InputStreamReader r = new InputStreamReader(resource.getInputStream())) {
            String script = CharStreams.toString(r);

            String url = request.getRequestURL().toString().toLowerCase();

            url = url.substring(0, url.indexOf("cli/"));

            script = script.replace("TRIDENT_TEMPLATE_URL", url);

            response.getWriter().println(script);
        }
    }

    @RequestMapping(path = "/cli/download")
    public void downloadClient(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setHeader("Content-Disposition", "attachment; filename=trident");

        OutputStream os = response.getOutputStream();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        try (InputStream is = cl.getResourceAsStream("cli/trident")) {
            ByteStreams.copy(is, os);
        }

        os.close();
    }
}
