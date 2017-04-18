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
 * Created on December 2, 2013, 11:34 PM
 * Retrieve bib and item data by bibid, OCLC MRN, ISBN/ISSN.
 *
 * Updated on May 6, 2016 4:10 PM: 
 * - Display serial item enum and chron
 * - Add barcode status (active, inactive)
 * - Add item stat description (RestrictedSpecColl,etc)
 * - Add item status (Not Charged, Charged, Renewed, Overdue, Recall Request, Hold Request etc)
 * 
 * BibItemLookup.java
 */
package src;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.JSONObject;
//import org.json.JSONArray;
import java.util.regex.*; 

public class GetBibItem extends HttpServlet {
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
//   Enumeration enu = request.getParameterNames();
        Pattern p = null;
        Matcher m = null;
        Map parmMap = request.getParameterMap();
        String  bibid = "", itemid = "", isxn = "", oclcmrn = "", sql866 = "", sqlMICount = "", sqlCount = "";
        String sql = "", sqlText = "", sqlOMR = "", sqlMfhd = "", sqlISBN = "", sqlISSN = "", sqlCallno = "";
        String itemId = "", itemStat = "", itemStatus = "", sqlItemStat = "", sqlItemStatus = "", sqlItem = "";
        String itemenum = "", chron = "", dateString = "", buff = "";
        int i = 0, j = 0, itemCount = 0, iPrint = 0; 
        PrintWriter out = null;
        PreparedStatement pstmt = null, pstmtText = null, pstmtOMR = null, pstmtMfhd = null, pstmtISBN = null, pstmt866 = null, pstmtItem = null;
        PreparedStatement pstmtMICount = null, pstmtCount = null, pstmtISSN = null, pstmtCallno = null, pstmtItemStat = null, pstmtItemStatus = null;
        ResultSet rs = null, rsText = null, rsOMR = null, rsMfhd = null, rsISBN = null, rsISSN = null, rsCallno = null;
        ResultSet rs866 = null, rsMICount = null, rsCount = null, rsItem = null, rsItemStat = null, rsItemStatus = null;
        Connection con = null;
        JSONObject json = new JSONObject();              
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yy HH:mm:ss");
        boolean available = false, isSPM = false, isMPM = false, isSerial = false, isOther = false, hasItem = false;
//  String contentType = request.getContentType();
            
        try {
/*   System.out.println(System.getProperty("catalina.base"));
       catalina.home: C:\Program Files\netbeans-5.5.1\enterprise3\apache-tomcat-5.5.17 
       catalina.base: C:\Users\yj33\.netbeans\5.5.1\apache-tomcat-5.5.17_base
*/
          
//      Statement stmt = Precompile.InitPrecompile.con.createStatement();
//      rs = stmt.executeQuery("SELECT * from patron where patron_id=153015");
            
// With this type, the browser tries to open JSON as a file.
            response.setContentType("application/json; charset=UTF-8");
            con = Precompile.InitPrecompile.ds.getConnection();
      //      response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Cache-Control", "nocache");
            request.setCharacterEncoding("UTF-8"); 
            response.setCharacterEncoding("UTF-8"); 
            out = response.getWriter();
            
            if (parmMap.containsKey("bibid")) {
                bibid = request.getParameter("bibid").trim();   
// Match digits only.
                if (bibid.matches("^[0-9]+$"))   
                   available = true;
            }  
                 
            if (parmMap.containsKey("isxn")) 
                isxn = request.getParameter("isxn").trim().toUpperCase();   
              
            if (parmMap.containsKey("oclcmrn")) {
                oclcmrn = request.getParameter("oclcmrn").trim();  
// Extract digits piece.                   
                p = Pattern.compile("[0-9]+");
                m = p.matcher(oclcmrn);
                if (m.find())                   
                   oclcmrn = m.group();                
            }
/*
                while (enu.hasMoreElements()) {
                pName = enu.nextElement().toString();
                if (pName.equals("bibid")) {
                   bibid = request.getParameter("bibid").trim();   
                   available = true;
                }                 
                if (pName.equals("isxn")) {
                   isxn = request.getParameter("isxn").trim();   
                }                               
                if (pName.equals("oclcmrn")) {
                   oclcmrn = request.getParameter("oclcmrn").trim();   
                }  
            }
*/     
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
            
// Find bibid by searching OCLC MR number. e.g. display_heading='ocm00000030'
            sqlOMR = "select * from bib_index"
                   + " where index_code = '079A'"
                   + " and normal_heading = ?";
//+ " and normal_heading='00000030'";
            pstmtOMR = con.prepareStatement(sqlOMR); 
 
// Find mfhd with or without item attached. e.g. bib_id=3941
            sqlMfhd = "select bib_mfhd.bib_id, bib_mfhd.mfhd_id, mfhd_item.item_id"
                    + " from bib_mfhd, mfhd_item"
                    + " where bib_mfhd.mfhd_id = mfhd_item.mfhd_id(+)"
                    + " and bib_mfhd.bib_id = ?";
            pstmtMfhd = con.prepareStatement(sqlMfhd); 

// Find item enum, chron. item_id = 9848717
            sqlItem = "select * from mfhd_item"
                      + " where item_id = ?";
            pstmtItem = con.prepareStatement(sqlItem);  
            
// Find call number.
            sqlCallno = "select display_call_no from mfhd_master"
                      + " where mfhd_id = ?";
            pstmtCallno = con.prepareStatement(sqlCallno);  
                    
// Find bibid by searching ISBN e.g. 3327000409, 0670119911
            sqlISBN = "select * from bib_index"
                    + " where index_code='020A'"
                    + " and upper(display_heading) = ?";
            pstmtISBN = con.prepareStatement(sqlISBN);  
        
// Find bibid by searching ISSN e.g. 0002-094X
            sqlISSN = "select * from bib_index"
                    + " where index_code='022A'"
                    + " and upper(display_heading) = ?";
            pstmtISSN = con.prepareStatement(sqlISSN);  
           
// Search bib data. 300 field - Physical Description, e.g. xv, 814 p. 25 cm. 
            sqlText = "select title_brief, author, publisher, publisher_date, pub_place"
                    + ", isbn, issn, yaledb.GETBIBTAG(bib_id,'300') as tag300"
                    + ", bib_format, yaledb.GETBIBTAG(bib_id,'079') as tag079"
                    + " from bib_text"
                    + " where bib_id = ?";
            pstmtText = con.prepareStatement(sqlText);  
             
            // Check if the mfhd has the 866 field for the purpose of MPM. 
            sql866 = "select yaledb.GetMFHDtag(Bib_Mfhd.mfhd_id,'866') as tag866 "
                   + "from Bib_Mfhd, Mfhd_Data "
                   + "where Bib_Mfhd.bib_id = ? "
                   + "and Bib_Mfhd.mfhd_id = Mfhd_Data.mfhd_id";
            pstmt866 = con.prepareStatement(sql866);  
         
            // Get item total count per bib's mfhd.
            sqlMICount = "Select Mfhd_Item.mfhd_id, count(*) as itemCount "
                     + "from Bib_Mfhd, Mfhd_Item, Item "
                     + "where Bib_Mfhd.bib_id = ? "
                     + "and Bib_Mfhd.mfhd_id = Mfhd_Item.mfhd_id "
                     + "and Mfhd_Item.item_id = Item.item_id "
                     + "group by Mfhd_Item.mfhd_id";
            pstmtMICount = con.prepareStatement(sqlMICount);  
             
            // Find item's enum and chron.
            sqlCount = "Select distinct Mfhd_Item.mfhd_id, Mfhd_Item.item_enum, Mfhd_Item.chron "
                     + "from Bib_Mfhd, Mfhd_Item "
                     + "where Bib_Mfhd.bib_id = ? "
                     + "and Bib_Mfhd.mfhd_id = Mfhd_Item.mfhd_id ";
            pstmtCount = con.prepareStatement(sqlCount);  
            
// Search item data.
            sql = "select item_barcode.item_barcode"
                + ", (case item_barcode_status.barcode_status_type"
                + " when 1 then 'Active'"
                + " else 'Inactive' end) barcode_status"  
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
                + ", mfhd_item.item_enum"
                + ", mfhd_item.chron"
                + " from item"
                + ", item_barcode"
                + ", item_barcode_status"
                + ", item_type pt"
                + ", item_type tt"
                + ", location p"
                + ", location t"
                + ", mfhd_item"
                + ", mfhd_master"
                + ", circ_transactions"
                + " where mfhd_item.item_id = item.item_id"
                + " and item.perm_location = p.location_id"
                + " and item.temp_location = t.location_id(+)"
                + " and item.item_type_id = pt.item_type_id(+)"
                + " and item.temp_item_type_id = tt.item_type_id(+)"
                + " and item.item_id = circ_transactions.item_id(+)"
                + " and item.item_id = item_barcode.item_id"
                + " and item_barcode.barcode_status = item_barcode_status.barcode_status_type"
                + " and mfhd_item.mfhd_id = mfhd_master.mfhd_id"
                + " and item.item_id = ?";
//and item.item_id = '6433'";
            pstmt = con.prepareStatement(sql);   
// System.out.println(sql);  
            if (bibid == "") {
// Get bibid by searching OCLC MR number.
              if (oclcmrn != "") {
                pstmtOMR.setString(1,oclcmrn);               
                rsOMR = pstmtOMR.executeQuery();         
                if (rsOMR.next()) {
                    bibid = rsOMR.getString("bib_id");
                    available = true;
                } 
              }
             
// Get bibid by searching ISBN.  
              if (!available) {
                if (isxn != "") {
                    pstmtISBN.setString(1,isxn);               
                    rsISBN = pstmtISBN.executeQuery();         
                    if (rsISBN.next()) {
                        bibid = rsISBN.getString("bib_id");
                        available = true;
                    } 
                }
              }
             
// Get bibid by searching ISSN. 
              if (!available) {
                if (isxn != "") {
                    pstmtISSN.setString(1,isxn);               
                    rsISSN = pstmtISSN.executeQuery();         
                    if (rsISSN.next()) {
                        bibid = rsISSN.getString("bib_id").trim();
                        available = true;
                    } 
                }
              }
            }       
         
//    bibid="3941";
//    bibid="616193"; // a bunch of item records.
            out.println("{\"record\":[{");
  
            if (available) {                
// Get bib data.                        
               pstmtText.setString(1,bibid);               
               rsText = pstmtText.executeQuery();         
               if (rsText.next()) {         
/* 
                json.put("available", "Y"); 
                json.put("title", rsText.getString("title_brief"));
		json.put("author", rsText.getString("author"));
                json.put("pdescription", rsText.getString("tag300"));
                json.put("publisher", rsText.getString("publisher"));
		json.put("pubdate", rsText.getString("publisher_date"));
                json.put("pubplace", rsText.getString("pub_place"));
                out.println(json.toString());
*/                
          //      out.println("\"available\":" + "\"Y\",");
               
// UTF-8 can be displayed correctly with new String (rsText.getString("title_brief").getBytes ("iso-8859-1"), "UTF-8");
// plus response.setContentType("text/html;charset=UTF-8") together.
                if (rsText.getString("title_brief") != null) {
                 //   buff = new String (rsText.getString("title_brief").getBytes ("iso-8859-1"), "UTF-8");
                 //   buff = new String (rsText.getString("title_brief").getBytes ("ISO8859_1"));
                    buff = new String (rsText.getBytes("title_brief"), "UTF8");
  /*              
//byte[] streetBytes = street.getBytes(StandardCharsets.ISO_8859_1);
byte[] streetBytes = rsText.getBytes("title_brief");
String converted = new String(streetBytes, "UTF8"); 
//buff = new String (streetBytes, StandardCharsets.UTF_8);
buff = new String (streetBytes, StandardCharsets.UTF_8);
String arabic = "عربيStreitkräfte";
//arabic = new String (rsText.getString("title_brief").getBytes ("ISO8859_1"), "UTF8");
arabic = new String(rsText.getString("title_brief").getBytes("iso-8859-1"),"UTF-8");

buff= new String(arabic.getBytes("UTF-8"),"UTF-8");
buff=converted;*/
                }
                else
                    buff = "NA";
                out.println("\"title\":" + "\"" + xmlEntities(buff.trim()) + "\",");
                
                if (rsText.getString("author") != null)
                   // buff = new String (rsText.getString("author").getBytes ("ISO-8859-1"), "UTF-8");
                    buff = new String (rsText.getBytes("author"), "UTF8");
                else
                    buff = "NA";
                out.println("\"author\":" + "\"" + xmlEntities(buff.trim()) + "\",");
                
                if (rsText.getString("tag300") != null)
                  //  buff = new String (rsText.getString("tag300").getBytes ("iso-8859-1"), "UTF-8");
                    buff = new String (rsText.getBytes("tag300"), "UTF8");
                else
                    buff = "NA";
                out.println("\"pdescription\":" + "\"" + xmlEntities(buff.trim()) + "\",");
                
                if (rsText.getString("publisher") != null)
                  //  buff = new String (rsText.getString("publisher").getBytes ("iso-8859-1"), "UTF-8");
                    buff = new String (rsText.getBytes("publisher"), "UTF8");
                else
                    buff = "NA";
                out.println("\"publisher\":" + "\"" + xmlEntities(buff.trim()) + "\",");
                
                if (rsText.getString("pub_place") != null)
                  //  buff = new String (rsText.getString("pub_place").getBytes ("iso-8859-1"), "UTF-8");
                    buff = new String (rsText.getBytes("pub_place"), "UTF8");
                else
                    buff = "NA";
                out.println("\"pubplace\":" + "\"" + xmlEntities(buff.trim()) + "\",");
                
                if (rsText.getString("publisher_date") != null)
                    buff = rsText.getString("publisher_date");
                else
                    buff = "NA";
                out.println("\"pubdate\":" + "\"" + buff.trim() + "\",");
                
                if (rsText.getString("isbn") != null)
                    buff = rsText.getString("isbn");
                else {
                    if (rsText.getString("issn") != null)
                        buff = rsText.getString("issn");
                    else 
                        buff = "NA";
                }
                out.println("\"isxn\":" + "\"" + buff.trim() + "\",");
                
                if (rsText.getString("tag079") != null)
                    buff = rsText.getString("tag079");
                else
                    buff = "NA";
                out.println("\"oclcmrn\":" + "\"" + buff.trim() + "\",");
                
                out.println("\"bibid\":" + "\"" + bibid + "\",");
                              
// Retrieve mfhd with or without item attached.
                out.println("\"items\":[");
                          
                pstmtMfhd.setString(1,bibid);               
                rsMfhd = pstmtMfhd.executeQuery();   
                i = 0; 
                while (rsMfhd.next()) {            
                /*    if (i > 0) 
                        out.println(",");
                    i++; */
                    itemid = rsMfhd.getString("item_id"); 
                    if (itemid == null) {
                        json = new JSONObject(); 
                     //   json.put("mfhdexist", "Y"); 
                        json.put("mfhdid", rsMfhd.getString("mfhd_id")); 
// Get call number.                    
                        pstmtCallno.setString(1,rsMfhd.getString("mfhd_id"));               
                        rsCallno = pstmtCallno.executeQuery();         
                        if (rsCallno.next()) {
                           if (rsCallno.getString("display_call_no") != null) 
                              json.put("callno", rsCallno.getString("display_call_no"));
                            else 
                              json.put("callno", "NA");
                        } 
                    //    json.put("itemformat", "NA");
                        json.put("itemid", "0"); 
                        if (i++ > 0) 
                          out.println(",");
                        out.println(json.toString());
                    }
                    else {
// Distinguish if it's serial, or SPM or MPM or something else.   
                    isSerial = false;
                    if (rsText.getString("bib_format").equals("as")) {
                     //   json.put("itemformat", "Serial"); 
                        isSerial = true;
                    }
                    else {
                        if (rsText.getString("bib_format").equals("am")) {
                            isSPM = isMPM = hasItem = false;  
                            pstmt866.setString(1, bibid);  
                            rs866 = pstmt866.executeQuery();    
                            rs866.next(); 
                            if (rs866.getString(1) != null) {
                                isMPM = true;
                            }
                            else { 
                                pstmtMICount.setString(1, bibid);
                                rsMICount = pstmtMICount.executeQuery(); 
                                while (rsMICount.next()) { 
                                    if (rsMICount.getInt("itemCount") > 1) {
                                        isMPM = true;
                                        break;
                                    }
                                    else {
                                        isSPM = true;
                                    }
                                }
                   
                                if ((!isSPM) && (!isMPM)) {
                                    isSPM = true;
                                    isMPM = false;
                                }
      
                                if (isMPM) {
                                    itemCount = iPrint = 0;
                                    itemenum = chron = "";
                                    pstmtCount.setString(1, bibid);
                                    rsCount = pstmtCount.executeQuery(); 
                                    while (rsCount.next()) { 
                                        hasItem = true;
                                        itemenum = rsCount.getString("item_enum");
                                        chron = rsCount.getString("chron"); 
// Check if item_enum has CD or DVD.                       
                                        if ((itemenum != "") && (itemenum != null)) { 
                                            itemenum = itemenum.toUpperCase();
                                            if ((itemenum.indexOf("CD") < 0)
                                                && (itemenum.indexOf("DVD") < 0)
                                                && (itemenum.indexOf("ANSWER") < 0)
                                                && (itemenum.indexOf("MAP") < 0)
                                                && (itemenum.indexOf("PLAN") < 0)
                                                && (itemenum.indexOf("CARTE") < 0)
                                                && (itemenum.indexOf("PORTFOLIO") < 0)) {
                                                iPrint++;
                                            }
                                        }
// item_enum doesn't have CD or DVD. Check if item chron has CD or DVD. 
                                        else { 
                                            if ((chron != "") && (chron != null)) {
                                                chron = chron.toUpperCase();
                                                if ((chron.indexOf("CD") < 0)
                                                    && (chron.indexOf("DVD") < 0)
                                                    && (chron.indexOf("ANSWER") < 0)
                                                    && (chron.indexOf("MAP") < 0)
                                                    && (chron.indexOf("PLAN") < 0)
                                                    && (chron.indexOf("CARTE") < 0)
                                                    && (chron.indexOf("PORTFOLIO") < 0)) {
                                                    iPrint++;
                                                }
                                            }  
                                        }
                         
                                        if (iPrint > 1) {
                                            isMPM = true;
                                            isSPM = false;
                                            break;
                                        }
                                        else {
                                            isSPM = true;
                                        } 
                                        itemCount++; 
                                    } 
                                    if (!hasItem)
                                        isSPM = true; 
                                }
                            } 
                        }
// It is not either serial, or SPM or MPM (not am or as). It's something else.                 
                        else {
                            isOther = true;
                            json.put("itemformat", "Other"); 
                        }               
                    }
               
                    if (isSPM) {
                        json.put("itemformat", "SPM");  
                        json.put("itemenum", "NA"); 
                        json.put("itemchron", "NA");
                    }
                
                    //if (isMPM) {
                    if ((isMPM) || (isSerial)) { 
                        if (isMPM) 
                            json.put("itemformat", "MPM"); 
                        if (isSerial)
                            json.put("itemformat", "Serial"); 
                        itemenum = chron = "";
                        pstmtItem.setString(1, itemid);
                        rsItem = pstmtItem.executeQuery(); 
                        if (rsItem.next()) { 
                            itemenum = rsItem.getString("item_enum");
                            itemenum = (itemenum == null ? "NA" : itemenum);
                            json.put("itemenum", itemenum); 
                          
                            chron = rsItem.getString("chron");
                            chron = (chron == null ? "NA" : chron);
                            json.put("itemchron", chron); 
                        }
                        else {
                            json.put("itemenum", "NA"); 
                            json.put("itemchron", "NA"); 
                        }
                    } 
// Retrieve item data.                      
                        pstmt.setString(1,itemid);               
                        rs = pstmt.executeQuery(); 
                        j = 0;
                        //if (rs.next()) {  
                        while (rs.next()) { 
                        //   json.put("mfhdexist", "Y"); 
                            if (isOther) {
                                itemenum = chron = "";
                                itemenum = rs.getString("item_enum");
                                itemenum = (itemenum == null ? "NA" : itemenum);
                                json.put("itemenum", xmlEntities(itemenum));
                                chron = rs.getString("chron");
                                chron = (chron == null ? "NA" : chron);
                                json.put("itemchron", xmlEntities(chron)); 
                                isOther = false;
                            }
                            if (j > 0) { 
                               out.println(",");
                               if ((isMPM) || (isSerial)) {
                                   if (isMPM) 
                                      json.put("itemformat", "MPM"); 
                                   if (isSerial)
                                      json.put("itemformat", "Serial"); 

                                   itemenum = chron = "";         
                                   itemenum = rs.getString("item_enum");
                                   itemenum = (itemenum == null ? "NA" : itemenum);
                                   json.put("itemenum", itemenum); 

                                   chron = rs.getString("chron");
                                   chron = (chron == null ? "NA" : chron);
                                   json.put("itemchron", chron); 
                               }
                            }
                            
                            itemId = itemStat = itemStatus = ""; 
                            itemId = rsMfhd.getString("item_id");  
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
             
                            json.put("mfhdid", rsMfhd.getString("mfhd_id")); 
                            json.put("itemid", itemId); 
                            json.put("barcode", rs.getString("item_barcode"));
                            json.put("barcodestatus", rs.getString("barcode_status"));
                            json.put("itemstat", itemStat);
                            json.put("itemstatus", itemStatus);    
                            json.put("itemspinelabel", (rs.getString("spine_label") == null ? "NA" : rs.getString("spine_label")));
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
                            if (i++ > 0) 
                                out.println(",");
                            out.println(json.toString());  
                            j++;
                        }
                    }
                }
                if (i == 0) {
                   json.put("mfhdid", "0"); 
                   out.println(json.toString()); 
                }
                out.println("]");
               } 
               else {
                 out.println("\"bibid\":" + "\"0\"");
               }
        
               rs = null;
               rsText = null;
               rsOMR = null;
               rsMfhd = null;
               rsISBN = null;
               rsISSN = null;
               rsCallno = null;
               rs866 = null;
               rsMICount = null;
               rsCount = null;
               rsItem = null;
               rsItemStat = null;
               rsItemStatus = null;
            }                
            else {
                 out.println("\"bibid\":" + "\"0\"");
            }System.out.println(json.toString());
            out.println("}]}");
            
            out.flush();
            out.close();
                   
            rs = null;
            rsText = null;
            rsOMR = null;
            rsMfhd = null;
            rsISBN = null;
            rsISSN = null;
            rsCallno = null;
            rs866 = null;
            rsMICount = null;
            rsCount = null;
            rsItem = null;
            rsItemStat = null;
            rsItemStatus = null;
            pstmt.close();
            pstmtText.close();
            pstmtOMR.close();
            pstmtMfhd.close();
            pstmtISBN.close(); 
            pstmtISSN.close();
            pstmtCallno.close();  
            pstmt866.close(); 
            pstmtMICount.close();
            pstmtCount.close();
            pstmtItem.close();
            pstmtItemStat.close();
            pstmtItemStatus.close();
            con.close();
      } catch (SQLException sqle) {
            System.err.println("SQL error in VAPI GetBibItem: " + sqle.getMessage());         
    } catch (Throwable anything) {
        System.err.println("Abnormal exception caught in VAPI GetBibItem: " + anything);
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
            /*    case '"':
                    sb.append("&quot;");
                    break; */
//Remove the quote.
                case '"':
                    sb.append(""); 
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