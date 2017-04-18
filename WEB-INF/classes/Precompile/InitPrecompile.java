/*
 *  Copyright (c) 2013-2017 Yale University. All rights reserved.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS," AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE EXPRESSLY
 *  DISCLAIMED. IN NO EVENT SHALL YALE UNIVERSITY OR ITS EMPLOYEES BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED, THE COSTS OF
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED IN ADVANCE OF THE POSSIBILITY OF SUCH
 *  DAMAGE.
 *
 *  Redistribution and use of this software in source or binary forms,
 *  with or without modification, are permitted, provided that the
 *  following conditions are met:
 *
 *  1. Any redistribution must include the above copyright notice and
 *  disclaimer and this list of conditions in any related documentation
 *  and, if feasible, in the redistributed software.
 *
 *  2. Any redistribution must include the acknowledgment, "This product
 *  includes software developed by Yale University," in any related
 *  documentation and, if feasible, in the redistributed software.
 *
 *  3. The names "Yale" and "Yale University" must not be used to endorse
 *  or promote products derived from this software.
 */
 /* 
 *  Author  Yue Ji 
 *  Created on July 15, 2016, 3:10 PM
 *  InitPrecompile.java
 */
package Precompile;

import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import javax.sql.*;
import java.io.FileInputStream;
import java.util.*;
import javax.naming.*;
import java.io.*;
import java.lang.*;
import java.util.StringTokenizer;

public class InitPrecompile extends HttpServlet {
public static Properties initProp = new java.util.Properties();  
public static DataSource ds = null;

    public void init(ServletConfig config) throws ServletException {
        String dir = "";
        Context initCtx = null, envCtx = null;
        super.init(config);
        System.out.println("");
      
        dir = getServletContext().getRealPath("/");
	            
        try {           
           initProp.load(new FileInputStream(dir + "WEB-INF" + System.getProperty("file.separator") 
                + "classes" + System.getProperty("file.separator") + "Precompile" 
                + System.getProperty("file.separator") + "Init.properties"));
        } catch(Exception e) {
            System.err.println("InitPrecompile.java has error in reading Init.properties in VoyUpdate: " + e.getMessage());
        }
            
        try {
// Open database connection. 
           initCtx = new InitialContext();
           envCtx = (Context) initCtx.lookup("java:comp/env");
           ds = (DataSource) envCtx.lookup("jdbc/ReportsDB");         
        } catch (NamingException e) {
            System.out.println("VoyUpdate InitPrecompile failed to get context " + e);
        }  
    }
   
/** Destroys the servlet. */
    public void destroy() {
// Close database connetion
       ds = null;
    }
}