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
 * Author  Yue Ji 
 * Created on November 20, 2013, 2:31 PM
 * Retrieve item data by item barcode.
 *
 * Updated on May 10, 2016 10:00 AM:
 * - Add barcode status (active, inactive)
 * - Add item stat description (RestrictedSpecColl,etc)
 * - Add item status (Not Charged, Charged, Renewed, Overdue, Recall Request, Hold Request etc)
 * 
 * GetItem.java
 */
package src;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.JSONObject;
//import org.json.JSONArray;

public class GetItem extends HttpServlet {
    private ServletConfig config = null;
                   
/** Initialize global variables */
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        config = servletConfig;
    }

/** Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
*/
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        Enumeration enu = request.getParameterNames();
        String pname = "", itemBarcode = "", sql = "", sqlItemStat = "", sqlItemStatus = "", dateString = "";
        String mfhdId = "", itemId = "", itemStat = "", itemStatus = "";
        int i = 0; 
        PrintWriter out = null;
        PreparedStatement pstmt = null, pstmtItemStat = null, pstmtItemStatus = null;
        ResultSet rs = null, rsItemStat = null, rsItemStatus = null;
        Connection con = null;
        JSONObject json = new JSONObject();              
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yy HH:mm:ss");
      
//  String contentType = request.getContentType();
            
        try {
  /*  System.out.println(System.getProperty("catalina.base")); System.out.println(System.getProperty("catalina.home"));
       catalina.home: C:\Program Files\netbeans-5.5.1\enterprise3\apache-tomcat-5.5.17 
       catalina.base: C:\Users\yj33\.netbeans\5.5.1\apache-tomcat-5.5.17_base
*/
          
//      Statement stmt = Precompile.InitPrecompile.con.createStatement();
//      rs = stmt.executeQuery("SELECT * from patron where patron_id=153015");
            
/* With this type, the browser tries to open as a file. For the web service, please comment out.
  response.setContentType("application/json"); */                        
            con = Precompile.InitPrecompile.ds.getConnection();
    //      response.setContentType("text/html;charset=UTF-8");
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "nocache");
            request.setCharacterEncoding("UTF-8"); 
            response.setCharacterEncoding("UTF-8"); 
            out = response.getWriter();
                 
            sql = "select item_barcode.item_barcode"
                + ", (case item_barcode_status.barcode_status_type"
                + " when 1 then 'Active'"
                + " else 'Inactive' end) barcode_status"  
                + ", item_barcode.item_id"
                + ", (case item.temp_location"
                + " when 0 then p.location_code" 
                + " else t.location_code end) loc_code"  
                + ", (case item.temp_location"
                + " when 0 then p.location_display_name" 
                + " else t.location_display_name end) loc_dis_name"
                + ", (case item.temp_location"
                + " when 0 then pt.item_type_code" 
                + " else tt.item_type_code end) type_code"  
                + ", (case item.temp_location"
                + " when 0 then pt.item_type_display"
                + " else tt.item_type_display end) type_dis_name"
                + ", item.spine_label"
                + ", circ_transactions.current_due_date"
                + ", mfhd_master.display_call_no"
                + ", bib_item.bib_id"
                + ", mfhd_item.mfhd_id"
                + ", mfhd_item.item_enum"
                + ", mfhd_item.chron"
                + " from item_barcode"
                + ", item_barcode_status"
                + ", item"
                + ", item_type pt"
                + ", item_type tt"
                + ", location p"
                + ", location t"
                + ", mfhd_item"
                + ", mfhd_master"
                + ", circ_transactions"
                + ", bib_item"
                + " where item.perm_location = p.location_id"
                + " and item.temp_location = t.location_id(+)"
                + " and item_barcode.item_id = item.item_id"
                + " and item_barcode.item_id = mfhd_item.item_id"
                + " and item.item_type_id = pt.item_type_id(+)"
                + " and item.temp_item_type_id = tt.item_type_id(+)"
                + " and item_barcode.item_id = circ_transactions.item_id(+)"
                + " and mfhd_item.mfhd_id = mfhd_master.mfhd_id"
                + " and item_barcode.item_id = bib_item.item_id"
                + " and item_barcode.barcode_status = item_barcode_status.barcode_status_type";
            
            while (enu.hasMoreElements()) {
                pname = enu.nextElement().toString();
                if (pname.equals("barcode")) {
                   itemBarcode = request.getParameter("barcode").trim();  
                   sql += " and mfhd_item.mfhd_id = (select distinct mfhd_item.mfhd_id"
                           + " from item_barcode, mfhd_item"
                           + " where item_barcode.item_id = mfhd_item.item_id"
                           + " and item_barcode.item_barcode = ?)";
//+ " and item_barcode.item_barcode = '39002031021208')" /* has due 39002035541755 */
                   pstmt = con.prepareStatement(sql);              
                   pstmt.setString(1,itemBarcode);               
                }  
                
                if (pname.equals("itemid")) {
                   itemId = request.getParameter("itemid").trim();   
                   sql += " and mfhd_item.item_id = ?";
                   pstmt = con.prepareStatement(sql);              
                   pstmt.setString(1,itemId);               
                }
                
                if (pname.equals("mfhdid")) {
                   mfhdId = request.getParameter("mfhdid").trim();  
                   sql += " and mfhd_item.mfhd_id = ?";
                   pstmt = con.prepareStatement(sql);              
                   pstmt.setString(1,mfhdId);               
                }
            }
// Retrieve item stats query.
            sqlItemStat = "select item_stat_code.item_stat_code_desc"
                        + " from item, item_stats, item_stat_code"
                        + " where item.item_id = item_stats.item_id(+)" 
                        + " and item_stats.item_stat_id = item_stat_code.item_stat_id(+)"
                        + " and item.item_id = ?";
            pstmtItemStat = con.prepareStatement(sqlItemStat); 
             
// Retrieve item status query.
            sqlItemStatus = "select item_status_type.item_status_desc"
                        + " from item, item_status, item_status_type"
                        + " where item.item_id = item_status.item_id(+)" 
                        + " and item_status.item_status = item_status_type.item_status_type(+)"
                        + " and item.item_id = ?";
            pstmtItemStatus = con.prepareStatement(sqlItemStatus);  
                                                                   
 //System.out.println(sql);
            
            rs = pstmt.executeQuery(); 
            i = 0;
            
            out.println("{\"items\":[");
            while (rs.next()) {
                if (i > 0)
                    out.println(",");
                i++;
                
                itemId = itemStat = itemStatus = ""; 
                itemId = rs.getString("item_id");  
// Retrieve item stat.                
                pstmtItemStat.setString(1,itemId);               
                rsItemStat = pstmtItemStat.executeQuery(); 
                while (rsItemStat.next()) { 
                    itemStat += ((rsItemStat.getString("item_stat_code_desc") == null) ? "NA," : (rsItemStat.getString("item_stat_code_desc") + ','));
                }
                itemStat = itemStat.substring(0,itemStat.length()-1);
                         
// Retrieve item status.                
                pstmtItemStatus.setString(1,itemId);               
                rsItemStatus = pstmtItemStatus.executeQuery(); 
                while (rsItemStatus.next()) {
                    itemStatus += ((rsItemStatus.getString("item_status_desc") == null) ? "NA," : (rsItemStatus.getString("item_status_desc") + ','));
                }
                itemStatus = itemStatus.substring(0,itemStatus.length()-1);
                        
                json.put("barcode", rs.getString("item_barcode"));
                json.put("barcodestatus", rs.getString("barcode_status"));
                json.put("itemid", itemId);
                json.put("itemstat", itemStat);
                json.put("itemstatus", itemStatus);   
                json.put("itemspinelabel", (rs.getString("spine_label") == null ? "NA" : rs.getString("spine_label")));
                json.put("itemenum", (rs.getString("item_enum") == null ? "NA" : rs.getString("item_enum")));
                json.put("itemchron", (rs.getString("chron") == null ? "NA" : rs.getString("chron")));
		json.put("loccode", rs.getString("loc_code"));
                json.put("locname", rs.getString("loc_dis_name"));
                
                if (rs.getString("type_code") != null) {
                    json.put("itypecode", rs.getString("type_code"));
                    json.put("itypename", rs.getString("type_dis_name"));
                }
                else {
                    json.put("itypecode", "NA");
                    json.put("itypename", "NA");
                }
                
                if (rs.getString("current_due_date") != null) {
                  dateString = sdf.format(rs.getTimestamp("current_due_date"));
                    json.put("availdate", dateString);
                }
                else
                    json.put("availdate", "NA");
                
                if (rs.getString("display_call_no") != null) 
                    json.put("callno", rs.getString("display_call_no"));
                else 
                    json.put("callno", "NA");
                                               
                json.put("bibid", rs.getString("bib_id"));
                json.put("mfhdid", rs.getString("mfhd_id"));
                out.println(json.toString());                
            }
                    
            if (i == 0) {
                json.put("barcode", "NA"); 
                out.println(json.toString());
            }
            
            out.println("]}");
// JSON example.
// {"msg":"OK","arr":[5,3,1],"map":{"key1":"val1","key2":"val2"},"status":200}             
//response.setContentType("application/json"); //With this type, the browser tries to open as a file.
/*		response.setHeader("Cache-Control", "nocache");
        	response.setCharacterEncoding("utf-8");
		 out = response.getWriter();

		JSONObject json = new JSONObject();

		// put some value pairs into the JSON object as into a Map.
		json.put("status", 200);
		json.put("msg", "OK");

		// put a "map" 
		JSONObject map = new JSONObject();
		map.put("key1", "val1");
		map.put("key2", "val2");
		json.put("map", map);
		
		// put an "array"
		JSONArray arr = new JSONArray();
		arr.put(5);
		arr.put(3);
		arr.put(1);
		json.put("arr", arr);

		// finally output the json string		
		out.print(json.toString());
*/               
            out.flush();
            out.close();
            rs = null;
            rsItemStat = null;
            rsItemStatus = null;
            pstmt.close();
            pstmtItemStat.close();
            pstmtItemStatus.close();
            con.close();
      } catch (SQLException sqle) {
            System.err.println("SQL error in VAPI GetItem: " + sqle.getMessage());         
    } catch (Throwable anything) {
        System.err.println("Abnormal exception caught in VAPI GetItem: " + anything);
    } 
    finally {
          try {
            out.flush();
            out.close(); 
            rs = null;
            rsItemStat = null;
            rsItemStatus = null;       
            pstmt.close();
            pstmtItemStat.close();
            pstmtItemStatus.close();
            con.close();
         } catch (SQLException sqle) {
            System.err.println(sqle.getMessage());
         } 
        }
  }
    
/** Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
*/
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, java.io.IOException {
        processRequest(request, response);
    }
    
/** Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
*/
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, java.io.IOException {
        processRequest(request, response);
    }
    
    public String replaceAllChars(String original, char find, String replacement) {
        String result = "";
        int i = 0;
        for (i = 0; i < original.length(); i++) {
            if (original.charAt(i) == find) {
                result = result + replacement;
            }
            else {
                result = result + original.charAt(i);
            }
        }
        return result;
    }
    
       
// Convert xml entities.
 public String xmlEntities(String s) {   
    StringBuffer sb = new StringBuffer("");

/*Also checks that no entities are converted twice by removing any already in the input string.   
  input = "Replace a with bbb in Java"; 
  output = input.replaceAll("a", "bbb");
  output = "Replbbbce bbb with bbb in Jbbbvbbb"; 
*/
    try {
        String sClean = new String(s);
    /*
    sClean = sClean.replaceAll("&amp;", "&");
    sClean = sClean.replaceAll("&quot;", "\"");
    sClean = sClean.replaceAll("&lt;", "<");
    sClean = sClean.replaceAll("&gt;", ">");
 */      
        for (int i = 0; i < sClean.length(); i++) {
            char c = sClean.charAt(i);
            switch(c) {
                case '&':
                    sb.append("&amp;");
                    break;
// apos is not html's entity. It is xml's entity.
                case '\'':
                    sb.append("&apos;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
//Carriage Return is '0D' in hex and '13' in decimal. This moves the cursor to the beginning of same line. 
                case 0x0D: 
                    sb.append("&#13;");
                    break;
// Line Feed is '0A' in hex and '10' indecimal. This moves the cursor to the next line at the same position. 
                case 0x0A:
                    sb.append("&#10;");
                    break;
                case 0x1E:
                case 0x1F:
                case 0x1D:
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
            }
        }
    } catch(Exception e) { 
      System.err.println("xmlEntities function happened error. " + e.getMessage());            
   }
   return sb.toString();      
 }
}