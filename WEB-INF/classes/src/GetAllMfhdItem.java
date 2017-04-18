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
 * Created on May 10 2016 2:40 PM
 * Retrieve one bib's all rmfhd and item record by item barcode, item id, mfhd id.
 * GetAllMfhdItem.java
 */
package src;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.net.*;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
//import org.marc4j.MarcXmlWriter;
import org.marc4j.MarcWriter;
import org.marc4j.marc.Record;
import org.marc4j.marc.*;

public class GetAllMfhdItem extends HttpServlet {
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
        
        Map parmMap = request.getParameterMap();
        String sqlBibItem = "", sqlItemPerm = "", sqlMfhdCL = "", sqlMfhd = "", sqlItem = "", sqlBarcode = "", sqlItemStatus = "", sqlItemStat = "";
        String recentDate = "", bibid = "", mfhdid = "", itemid = "", itemenum = "", chron = "", itemBarcode = "", itemStatus = "", sqlBibHistory = "", sqlMfhdHistory = "";
        String fromUser = "", fromServerString = "", mr = "", retData = "", tag = "", itemStat = "";
        int i = 0, j = 0, k = 0, l = 0, m = 0, i583 = 0, i856 = 0;
        String[] tag583 = new String[20];
        String[] tag856 = new String[20];
        PrintWriter out = null;
        PreparedStatement pstmtBibItem = null, pstmtMfhdData = null, pstmtItemPerm = null, pstmtMfhdCL = null, pstmtItem = null, pstmtItemStatus = null;
        PreparedStatement pstmtMfhd = null, pstmtBarcode = null, pstmtBibHistory = null, pstmtMfhdHistory = null, pstmtItemStat = null;
        ResultSet rsBibItem = null, rsMfhdData = null, rsItemPerm = null, rsMfhdCL = null, rsItem = null, rsMfhd = null, rsBarcode = null;
        ResultSet rsBibHistory = null, rsMfhdHistory = null, rsItemStat = null, rsItemStatus = null;
        Connection con = null;
        SimpleDateFormat sdfRecentDate = new SimpleDateFormat("MM-dd-yy HH:mm:ss");
        // Connect to socket server.
        int portNumber = 7010; // CAT port
        String moduleName = "CAT"; // CAT module
        DataOutputStream outputToServer = null;
        DataInputStream inputFromServer = null;
        Socket socketToServer = null;
        InputStream input = null;
        Reader sReader = null;
        StringBuffer buffer = new StringBuffer();
        int currChar = 1, preChar = 1;
        StringTokenizer retToken = null;
        boolean goodReturn = false;
        MarcReader reader = null;
        MarcWriter writer = null;
        Record record = null;
        List myList = null, subfields = null;
        Iterator iMyList, iSubfield;
        VariableField varField = null;
        DataField df = null, field = null;
        Subfield sf;
        char subfieldCode;
        
//  String contentType = request.getContentType();
        try {
/*   System.out.println(System.getProperty("catalina.base"));
       catalina.home: C:\Program Files\netbeans-5.5.1\enterprise3\apache-tomcat-5.5.17
       catalina.base: C:\Users\yj33\.netbeans\5.5.1\apache-tomcat-5.5.17_base
 */
            
//      Statement stmt = Precompile.InitPrecompile.con.createStatement();
//      rs = stmt.executeQuery("SELECT * from patron where patron_id=153015");

// With this type, the browser tries to open JSON as a file: response.setContentType("application/json; charset=UTF-8"); 
            con = Precompile.InitPrecompile.ds.getConnection();
            response.setContentType("application/xml;charset=UTF-8");
//  response.setContentType("text/xml;charset=UTF-8");
            response.setHeader("Cache-Control", "nocache");
            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");
            out = response.getWriter();
           
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            
// Find the most recent bib history action date, action type, operator id.
            sqlBibHistory = "select bib_history.operator_id, bib_history.action_type_id, bib_history.action_date"
                    + " from bib_history"
                    + ", (select bib_id, max(action_date) as recent_date"
                    + " from bib_history"
                    + " where bib_id = ?"
                    + " group by bib_id) recent_date"
                    + " where bib_history.bib_id = recent_date.bib_id"
                    + " and bib_history.action_date = recent_date.recent_date";
            pstmtBibHistory = con.prepareStatement(sqlBibHistory);
            
            // Find the most recent mfhd history action date, action type, operator id.
            sqlMfhdHistory = "select mfhd_history.operator_id, mfhd_history.action_type_id, mfhd_history.action_date"
                    + " from mfhd_history"
                    + ", (select mfhd_id, max(action_date) as recent_date"
                    + " from mfhd_history"
                    + " where mfhd_id = ?"
                    + " group by mfhd_id) recent_date"
                    + " where mfhd_history.mfhd_id = recent_date.mfhd_id"
                    + " and mfhd_history.action_date = recent_date.recent_date";
            pstmtMfhdHistory = con.prepareStatement(sqlMfhdHistory);
            
// Find mfhd with or without item attached. e.g. bib_id=3941
            /*sqlMfhd = "select bib_mfhd.mfhd_id, mfhd_item.item_id"
                    + " from bib_mfhd, mfhd_item"
                    + " where bib_mfhd.mfhd_id = mfhd_item.mfhd_id(+)"
                    + " and bib_mfhd.bib_id = ?";
            */
            sqlMfhd = "select bib_mfhd.mfhd_id from bib_mfhd"
                    + " where bib_mfhd.bib_id = ?";
            pstmtMfhd = con.prepareStatement(sqlMfhd);
            
// Find item enum, chron. item_id = 9848717.
            /*sqlItem = "select mfhd_Item.item_enum, mfhd_Item.chron from mfhd_item"
                    + " where item_id = ?"; */
            sqlItem = "select * from mfhd_item"
                    + " where mfhd_id = ?";
            pstmtItem = con.prepareStatement(sqlItem);
            
// Find other item barcode.
            sqlBarcode = "select item_barcode, barcode_status from item_barcode"
                    + " where item_id = ?";
            pstmtBarcode = con.prepareStatement(sqlBarcode);
            
// Find mfhd call no, location.
            sqlMfhdCL = "select mfhd_master.display_call_no"
                    + ", location.location_code, location.location_display_name"
                    + " from mfhd_master, location"
                    + " where mfhd_master.location_id = location.location_id"
                    + " and mfhd_master.mfhd_id = ?";
            pstmtMfhdCL = con.prepareStatement(sqlMfhdCL);
            
// Find item location and type.
            sqlItemPerm = "select location.location_code"
                    + ", location.location_display_name"
                    + ", item.spine_label"
                    + ", item_type.item_type_code"
                    + ", item_type.item_type_display"
                    + " from item, location, item_type"
                    + " where item.perm_location = location.location_id"
                    + " and item.item_type_id = item_type.item_type_id"
                    + " and item.item_id = ?";
            pstmtItemPerm = con.prepareStatement(sqlItemPerm);
            
// Retrieve item status.
            sqlItemStatus = "select item_status_type.item_status_desc"
                    + " from (select item.item_id, max(item_status.item_status_date) as current_date"
                    + " from item, item_status"
                    + " where item.item_id = item_status.item_id"
                    + " and item.item_id = ?"
                    + " group by item.item_id) current_status"
                    + ", item_status"
                    + ", item_status_type"
                    + " where current_status.item_id = item_status.item_id"
                    + " and item_status.item_status = item_status_type.item_status_type"
                    + " and nvl(item_status.item_status_date,DATE '0000-01-01') = nvl(current_status.current_date,DATE '0000-01-01')";
            pstmtItemStatus = con.prepareStatement(sqlItemStatus);
            
// Retrieve item stats query.
            sqlItemStat = "select item_stat_code.item_stat_code_desc"
                        + " from item, item_stats, item_stat_code"
                        + " where item.item_id = item_stats.item_id(+)" 
                        + " and item_stats.item_stat_id = item_stat_code.item_stat_id(+)"
                        + " and item.item_id = ?";
            pstmtItemStat = con.prepareStatement(sqlItemStat); 
             
// Get mfhd tag 583, tag 856.
/* GetMFHDtag has bug. It can only get the first tag if there are more than 1 same tag existed.
            sqlMfhdData = "select yaledb.GetMFHDtag(mfhd_id,'583') as tag583"
                   + ", yaledb.GetMFHDtag(mfhd_id,'856') as tag856"
                   + " from mfhd_data "
                   + " where mfhd_data.mfhd_id = ?";
            pstmtMfhdData = Precompile.InitPrecompile.con.prepareStatement(sqlMfhdData,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
 */
// Initialize Voayger API.           
            socketToServer = new Socket(Precompile.InitPrecompile.initProp.getProperty("Server_IP_address"),portNumber);
            outputToServer = new DataOutputStream(socketToServer.getOutputStream());
            inputFromServer = new DataInputStream(socketToServer.getInputStream());
            
            sReader = null;
            fromServerString = "";
            fromUser = "";
            fromUser = "[HEADER]"+'\0'+"CO=EISI"+'\0'+"AP="+moduleName+'\0'
                    +"VN=1.03"+'\0'+"TO=60"+'\0'+"RQ=INIT"+'\0'
                    +"[DATA]"+'\0'+"AP="+moduleName+'\0'
                    +"VN="+Precompile.InitPrecompile.initProp.getProperty("Voyager_version_number")+'\0'+'\0';
            outputToServer.write(fromUser.getBytes());
            sReader = new BufferedReader(new InputStreamReader(inputFromServer, "UTF-8"));
            buffer = null;
            buffer = new StringBuffer();
            while (true) {
                currChar = sReader.read();
                if ((preChar == 0) && (currChar == 0))
                    break;
                buffer.append((char)currChar);
                preChar = currChar;
            }
            fromServerString = buffer.toString();
            retToken = new StringTokenizer(fromServerString, "\0");
            goodReturn = false;
            while (retToken.hasMoreTokens()) {
                retData = retToken.nextToken();
                if (retData.toUpperCase().equalsIgnoreCase("RC=0"))  {
                    goodReturn = true;
                    break;
                }
            }
            if (!goodReturn) {
                System.err.println("Server socket cannot INITIALIZE in VAPI!");
                System.exit(1);
            }
            
// Get parameters from url input.            
            if (parmMap.containsKey("barcode")) {
                itemBarcode = request.getParameter("barcode").trim();
// Find bibid by searching item barcode.
                sqlBibItem = "select bib_item.bib_id from bib_item, item_barcode"
                        + " where bib_item.item_id = item_barcode.item_id"
                        + " and item_barcode.item_barcode = ?";
//+ " and item_barcode.item_barcode ='39002000884628'";
            } else if (parmMap.containsKey("itemid")) {
                itemid = request.getParameter("itemid").trim();
// Find bibid by searching item id.
                sqlBibItem = "select bib_item.bib_id from bib_item"
                        + " where bib_item.item_id = ?";
            } else if (parmMap.containsKey("mfhdid")) {
                mfhdid = request.getParameter("mfhdid").trim();
// Find bibid by searching item id.
                sqlBibItem = "select bib_mfhd.bib_id from bib_mfhd"
                        + " where bib_mfhd.mfhd_id = ?";
            } else if (parmMap.containsKey("bibid")) {
                bibid = request.getParameter("bibid").trim();
            }
// No record found.
            else {
                out.println("<record_list>");                
                out.println("<bib_id>NA</bib_id>");
                out.println("</record_list>");
                
                out.flush();
                out.close();
                return;
            }
      
// 1. Get bib id by searching item barcode.            
            out.println("<record_list>");
            if (bibid == "") {
                pstmtBibItem = con.prepareStatement(sqlBibItem,ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
                if (parmMap.containsKey("barcode")) {  
// Retrieve the bib id of this barcode.
                    pstmtBibItem.setString(1,itemBarcode);
                } else if (parmMap.containsKey("itemid")) {
// Retrieve the bib id of this item id.
                    pstmtBibItem.setString(1,itemid);
                } else if (parmMap.containsKey("mfhdid")) {
// Retrieve the bib id of this mfhd id.
                    pstmtBibItem.setString(1,mfhdid);
                }
        
                rsBibItem = pstmtBibItem.executeQuery(); 
// position on last row.
                rsBibItem.last();
// 2. If there are more than one bib ids, it's bound-with items. Preservation Dept doesn't want it.
                if (rsBibItem.getRow() > 1) {
                    out.println("<bib_id>bound-with</bib_id>");
                    out.println("</record_list>");
                    out.flush();
                    out.close();
                    rsBibItem = null;             
                    pstmtBibItem.close();
                    //    pstmtMfhdData.close();
                    pstmtItemPerm.close();
                    pstmtMfhdCL.close();
                    pstmtMfhd.close();
                    pstmtItem.close();
                    pstmtItemStatus.close();
                    pstmtItemStat.close();
                    pstmtBarcode.close();
                    pstmtBibHistory.close();
                    pstmtMfhdHistory.close();
                    outputToServer.close();
                    inputFromServer.close();
                    socketToServer.close();
                    return;
                }
// 3. If there is only one bib id, continue to find bib history.           
                rsBibItem.beforeFirst();
                if (rsBibItem.next()) {
                    bibid = rsBibItem.getString("bib_id");
                }
// No record found.
                else {
                    out.println("<bib_id>NA</bib_id>");
                    out.println("</record_list>");
                    out.flush();
                    out.close();

                    rsBibItem = null;              
                    pstmtBibItem.close();
                    //   pstmtMfhdData.close();
                    pstmtItemPerm.close();
                    pstmtMfhdCL.close();
                    pstmtMfhd.close();
                    pstmtItem.close();
                    pstmtItemStatus.close();
                    pstmtItemStat.close();
                    pstmtBarcode.close();
                    pstmtBibHistory.close();
                    pstmtMfhdHistory.close();
                    outputToServer.close();
                    inputFromServer.close();
                    socketToServer.close();
                    return;
                }
            }    
            out.println("<bib_id>" + bibid + "</bib_id>");
                
// Find the most recent bib history action date, action type, operator id.
            pstmtBibHistory.setString(1,bibid);
            rsBibHistory = pstmtBibHistory.executeQuery();
            if (rsBibHistory.next()) {
                out.println("<bib_action_type>" + rsBibHistory.getInt("action_type_id") + "</bib_action_type>");
                    
                if (rsBibHistory.getDate("action_date") != null) {
                    recentDate = sdfRecentDate.format(rsBibHistory.getDate("action_date"));
                } 
                else
                    recentDate = "NA";
                out.println("<bib_action_date>" + recentDate + "</bib_action_date>");                    
                out.println("<bib_operator_id>" + rsBibHistory.getString("operator_id") + "</bib_operator_id>");
            }
            rsBibHistory = null;
            rsBibItem = null;
                
// Find the most recent bib history action date, action type, operator id.
            pstmtBibHistory.setString(1,bibid);
            rsBibHistory = pstmtBibHistory.executeQuery();
            if (rsBibHistory.next()) {
                out.println("<bib_action_type>" + rsBibHistory.getInt("action_type_id") + "</bib_action_type>");
                    
                if (rsBibHistory.getDate("action_date") != null) {
                    recentDate = sdfRecentDate.format(rsBibHistory.getDate("action_date"));
                } 
                else
                    recentDate = "NA";
                out.println("<bib_action_date>" + recentDate + "</bib_action_date>");                   
                out.println("<bib_operator_id>" + rsBibHistory.getString("operator_id") + "</bib_operator_id>");
            }
            rsBibHistory = null;
            rsBibItem = null;
                
// 4. Already have one bib id, bib history. Now get mfhd id, item id.
            pstmtMfhd.setString(1,bibid);
            rsMfhd = pstmtMfhd.executeQuery();
            i = 0;
            while (rsMfhd.next()) {
                i++;                
// Retrieve mfhd with or without item attached.
                out.println("<holding>");
                
                mfhdid = rsMfhd.getString("mfhd_id");
                               
                out.println("<mfhd_id>" + mfhdid + "</mfhd_id>");
                
// Find the most recent Mfhd history action date, action type, operator id.
                pstmtMfhdHistory.setString(1,mfhdid);
                rsMfhdHistory = pstmtMfhdHistory.executeQuery();
                if (rsMfhdHistory.next()) {
                    out.println("<mfhd_action_type>" + rsMfhdHistory.getInt("action_type_id") + "</mfhd_action_type>");
                    
                    if (rsMfhdHistory.getDate("action_date") != null) {
                        recentDate = sdfRecentDate.format(rsMfhdHistory.getDate("action_date"));
                    } else
                        recentDate = "NA";
                    out.println("<mfhd_action_date>" + recentDate + "</mfhd_action_date>");
                    
                    out.println("<mfhd_operator_id>" + xmlEntities(rsMfhdHistory.getString("operator_id").toString()) + "</mfhd_operator_id>");
                }
                rsMfhdHistory = null;
 
// 5. Already have bib id, bib history, mfhd id, item id. Now get mfhd call number, location.
                pstmtMfhdCL.setString(1,mfhdid);
                rsMfhdCL  = pstmtMfhdCL.executeQuery();
                if (rsMfhdCL.next()) {
                    out.println("<mfhd_callno>" + (rsMfhdCL.getString("display_call_no") == null ? "NA" : xmlEntities(rsMfhdCL.getString("display_call_no").toString())) + "</mfhd_callno>");
                    out.println("<mfhd_loc_code>" + (rsMfhdCL.getString("location_code") == null ? "NA" : xmlEntities(rsMfhdCL.getString("location_code").toString())) + "</mfhd_loc_code>");
                    out.println("<mfhd_loc_name>" + (rsMfhdCL.getString("location_display_name") == null ? "NA" : xmlEntities(rsMfhdCL.getString("location_display_name").toString())) + "</mfhd_loc_name>");
                } else {
                    out.println("<mfhd_callno>NA</mfhd_callno>");
                    out.println("<mfhd_loc_code>NA</mfhd_loc_code>");
                    out.println("<mfhd_loc_name>NA</mfhd_loc_name>");
                }
                rsMfhdCL = null;

// 6. Already have bib id, bib history, mfhd id, item id, mfhd call number, location. Now get mfhd tag 583, tag 856.
// Get MFHD MARC record.
                fromUser = "";
                sReader = null;
                fromServerString = "";
              
                fromUser = "[HEADER]"+'\0'+"CO=EISI"+'\0'+"AP="+moduleName+'\0'+"VN=1.03"
                        +'\0'+"TO=60"+'\0'+"RQ=MFHD_RETRIEVE"+'\0'+"[DATA]"+'\0'
                        +"MI="+mfhdid+'\0'+'\0';
                outputToServer.write(fromUser.getBytes());
                
                sReader = new BufferedReader(new InputStreamReader(inputFromServer, "UTF-8"));
                
                buffer = null;
                buffer = new StringBuffer();
                while (true) {
                    currChar = sReader.read();
                    if ((preChar == 0) && (currChar == 0))
                        break;
                    buffer.append((char)currChar);
                    preChar = currChar;
                }
                fromServerString = buffer.toString();
                retToken = new StringTokenizer(fromServerString, "\0");
                goodReturn = false;
                
                while (retToken.hasMoreTokens()) {
                    retData = retToken.nextToken();
                    if (retData.toUpperCase().equalsIgnoreCase("RC=0"))  {
                        goodReturn = true;
                    }
                    
                    
                    if (retData.indexOf("MR=") >= 0)
                        mr = retData.substring(retData.indexOf("MR=")+3,retData.length());
                }
                //  System.out.println("mr="+mr);
                if (!goodReturn)
                    continue;
                
// Voyager API MFHD_RETRIEVE responsed correctly, now parsing the response to get mfhd tag 583, tag 856.
                input = null;
                reader = null;
                record = null;
                tag583 = tag856 = null;
                tag583 = new String[20];
                tag856 = new String[20];
                input = new ByteArrayInputStream(mr.getBytes("UTF-8"));
//input = new FileInputStream("a.bib");
                reader = new MarcStreamReader(input, "UTF8");
                i583 = i856 = 0;
                while (reader.hasNext()) {
                    record = reader.next();
                    myList= record.getVariableFields();
                    iMyList = myList.iterator();
                    while (iMyList.hasNext()) {
                        varField = (VariableField)iMyList.next();
                        if (!(varField instanceof ControlField)) {
                            df = (DataField)varField;
                            tag = df.getTag(); // tag e.g. 010, 245
                            
// Get all subfields data from tag 583.
                            if (tag.equals("583")) {
                                subfields = df.getSubfields(); // subfield e.g. a, b, c
                                iSubfield = subfields.iterator();
                                
                                while (iSubfield.hasNext()) {
                                    sf = (Subfield)iSubfield.next();
                                    if (tag583[i583] == null)
                                        tag583[i583] = xmlEntities(sf.getData().toString()) + ' ';
                                    else
                                        tag583[i583] = tag583[i583] + xmlEntities(sf.getData().toString()) + ' ';
                                }
                                i583++;
                            }
                            
                            // Get all subfields data from tag 856.
                            if (tag.equals("856")) {
                                subfields = df.getSubfields(); // subfield e.g. a, b, c
                                iSubfield = subfields.iterator();
                                while (iSubfield.hasNext()) {
                                    sf = (Subfield)iSubfield.next();
                                    if (tag856[i856] == null)
                                        tag856[i856] = xmlEntities(sf.getData().toString()) + ' ';
                                    else
                                        tag856[i856] = tag856[i856] + xmlEntities(sf.getData().toString()) + ' ';
                                }
                                i856++;
                            }
                        }
                    }
                }
             
     /*           pstmtMfhdData.setString(1,mfhdid);
                rsMfhdData = pstmtMfhdData.executeQuery();
                rsMfhdData.last();
    tag583 = new String[rsMfhdData.getRow()];
    tag856 = new String[rsMfhdData.getRow()];
      
    rsMfhdData.beforeFirst();
      
      
                while (rsMfhdData.next()) {
                    if (rsMfhdData.getString("tag583") != null)
                        tag583[i583++] = xmlEntities(rsMfhdData.getString("tag583").toString());
                 System.out.println(rsMfhdData.getString("tag583"));
      
                    if (rsMfhdData.getString("tag856") != null)
                      tag856[i856++] = xmlEntities(rsMfhdData.getString("tag856").toString());
                }
                out.println("<mfhd_583_field>" + (rsMfhdData.getString("tag583") == null ? "NA" : xmlEntities(rsMfhdData.getString("tag583").toString())) + "</mfhd_583_field>");
                out.println("<mfhd_856_field>" + (rsMfhdData.getString("tag856") == null ? "NA" : xmlEntities(rsMfhdData.getString("tag856").toString())) + "</mfhd_856_field>");
                rsMfhdData = null; 
      */
                if (i583 == 0)
                    out.println("<mfhd_583_field>NA</mfhd_583_field>");
                
                if (i856 == 0)
                    out.println("<mfhd_856_field>NA</mfhd_856_field>");
                                
                for(k = 0; k < i583; k++) {
                    out.println("<mfhd_583_field>" + tag583[k] + "</mfhd_583_field>");
                }
                
                for(k = 0; k < i856; k++) {
                    out.println("<mfhd_856_field>" + tag856[k] + "</mfhd_856_field>");
                }
                
                tag583 = tag856 = null;
                tag583 = new String[20];
                tag856 = new String[20];
                      
// 8. Already have bib id, bib history, mfhd id, item id, mfhd call number, location, mfhd tag 583, tag 856, other item active barcodes, 
//    item location, item type, item spine label. Now get item enum, chron.
                m = 0;
                
                pstmtItem.setString(1,mfhdid);
                rsItem = pstmtItem.executeQuery();
                while (rsItem.next()) {
                    m++;
                    out.println("<item>");
                    itemid = rsItem.getString("item_id");             
                    out.println("<item_id>" + itemid + "</item_id>");                
out.println("<item_enum>" + (rsItem.getString("item_enum") == null ? "NA" : xmlEntities(rsItem.getString("item_enum").toString())) + "</item_enum>");
                    out.println("<item_chron>" + (rsItem.getString("chron") == null ? "NA" : xmlEntities(rsItem.getString("chron").toString())) + "</item_chron>");   
              
// 7. Already have bib id, bib history, mfhd id, item id, mfhd call number, location, mfhd tag 583, tag 856, other item active barcodes.
//    Now get item location, item type, item spine label.
                pstmtItemPerm.setString(1,itemid);
                rsItemPerm = pstmtItemPerm.executeQuery();
                if (rsItemPerm.next()) {
                    out.println("<item_loc_code>" + (rsItemPerm.getString("location_code") == null ? "NA" : xmlEntities(rsItemPerm.getString("location_code").toString())) + "</item_loc_code>");
                    out.println("<item_loc_name>" + (rsItemPerm.getString("location_display_name") == null ? "NA" : xmlEntities(rsItemPerm.getString("location_display_name").toString())) + "</item_loc_name>");
                    out.println("<item_type_code>" + (rsItemPerm.getString("item_type_code") == null ? "NA" : xmlEntities(rsItemPerm.getString("item_type_code").toString())) + "</item_type_code>");
                    out.println("<item_type_name>" + (rsItemPerm.getString("item_type_display") == null ? "NA" : xmlEntities(rsItemPerm.getString("item_type_display").toString())) + "</item_type_name>");
                    out.println("<item_spine_label>" + (rsItemPerm.getString("spine_label") == null ? "NA" : xmlEntities(rsItemPerm.getString("spine_label").toString())) + "</item_spine_label>");
                } else {
                    out.println("<item_loc_code>NA</item_loc_code>");
                    out.println("<item_loc_name>NA</item_loc_name>");
                    out.println("<item_type_code>NA</item_type_code>");
                    out.println("<item_type_name>NA</item_type_name>");
                    out.println("<item_spine_label>NA</item_spine_label>");
                }
                rsItemPerm = null;
                
// 9. Already have bib id, bib history, mfhd id, item id, mfhd call number, location, mfhd tag 583, tag 856, other item active barcodes, 
//    item location, item type, item spine label. Now get item enum, chron. Now get item current status.
                pstmtItemStatus.setString(1,itemid);
                rsItemStatus = pstmtItemStatus.executeQuery();
                j = 0;
                itemStatus = "";
                while (rsItemStatus.next()) {
                    if (j == 0)
                        itemStatus = rsItemStatus.getString("item_status_desc");
                    else
                        itemStatus = itemStatus + ',' + rsItemStatus.getString("item_status_desc");
                    j++;
                }
                if (j > 0) {
                    out.println("<item_status>" + (((itemStatus == null) || (itemStatus == "")) ? "NA" : xmlEntities(itemStatus)) + "</item_status>");
                } else {
                    out.println("<item_status>NA</item_status>");
                }
                
// 10. Retrieve item stat.   
                itemStat = "";
                pstmtItemStat.setString(1,itemid);               
                rsItemStat = pstmtItemStat.executeQuery(); 
                while (rsItemStat.next()) {
                    itemStat += ((rsItemStat.getString("item_stat_code_desc") == null) ? "NA," : (rsItemStat.getString("item_stat_code_desc") + ','));
                }
                out.println("<item_stat>" + itemStat.substring(0,itemStat.length()-1) + "</item_stat>");
                                           
// 11. Already have bib id, bib history, mfhd id, item id, mfhd call number, location, mfhd tag 583, tag 856. 
//     Now get other item active barcodes associated with the same item id whose item barcode starts the search from the beginning.
                l = 0;
                pstmtBarcode.setString(1,itemid);
                rsBarcode = pstmtBarcode.executeQuery(); 
                while (rsBarcode.next()) {
                    out.println("<item_barcode>");
                    out.println("<barcode>" + (rsBarcode.getString("item_barcode") == null ? "NA" : xmlEntities(rsBarcode.getString("item_barcode").toString())) + "</barcode>");
                    out.println("<barcode_status>" + (rsBarcode.getInt("barcode_status") == 1 ? "Active" : "Inactive") + "</barcode_status>");
                    out.println("</item_barcode>");
                    l++;
                } 
                if (l == 0) {
                    out.println("<item_barcode>NA</item_barcode>");
                }
                rsBarcode = null;
                out.println("</item>");
                }
// The mfhd without item.                
                if (m == 0) {
                    out.println("<item>NA</item>");
                }
                
                out.println("</holding>");
                out.flush();
                
                rsItemStatus = null;
                rsItemStat = null;
                rsBibItem = null;
                //    rsMfhdData = null;
                rsItemPerm = null;
                rsMfhdCL = null;
                rsItem = null;
                rsBarcode = null;
                rsBibHistory = null;
                rsMfhdHistory = null;
            } // end while.
            
// No mfhd found.
            if (i == 0) {
                out.println("NA</holding>");
            }
            
            out.println("</record_list>"); 
            
        } catch (SQLException sqle) {
            System.err.println("SQL error in VAPI GetAllMfhdItem: " + sqle.getMessage());
        } 
        catch (Throwable anything) {
            System.err.println("Abnormal exception caught in VAPI GetAllMfhdItem: " + anything);
        }       
        finally {
          try {
           out.flush();
           out.close(); 
                  
           pstmtBibHistory.close();
           pstmtMfhdHistory.close();
           pstmtMfhd.close();
           pstmtItem.close();        
           pstmtBarcode.close();        
           pstmtMfhdCL.close();        
           pstmtItemPerm.close();        
           pstmtItemStatus.close(); 
           pstmtItemStat.close();
         //  pstmtBibItem.close();
           
           outputToServer.close();
           inputFromServer.close();
           socketToServer.close(); 
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
            } else {
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
            System.err.println("xmlEntities function happened error: " + e.getMessage());
        }
        return sb.toString();
    }
}