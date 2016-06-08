
<h1>API Netarchive search REST resources. </h1>
<h2>Version:${pom.version}</h2>
<h2>Build time:${build.time}</h2>
<br>                                                       
<h2> SERVICE METHODS: </h2>
<br>


<table class="table" border="1">  
       <caption><strong>HTTP GET</strong></caption>
        <thead>  
          <tr>  
            <th>URL</th>  
            <th>Input</th>                
            <th>Output</th>
          </tr>  
        </thead>  
        <tbody>                     
            <tr>  
            <td>services/search?searchText=xxx</td>  
            <td>
            Params:searchText
            </td>  
            <td>
              SearchResult
            </td>
          </tr>                                                                     
          <tr>     
            <td>services/image?arcFilePath=zzz&offset=xxx&height=xxx&width=yyy</td>  
            <td>
             Params:arcFilePath,offset,height,width
            </td>  
            <td>
              The image  
            </td>
          </tr>                
         <tr>     
            <td>services/downloadRaw?arcFilePath=xxx&offset=yyy</td>  
            <td>
             Params:arcFilePath,offset
            </td>  
            <td>
              Download the arc entry (any mimetype)  
            </td>
          </tr>                      
         
             <tr>     
            <td>services/findimages?searchText=xxx</td>  
            <td>
             Params:searchText
            </td>  
            <td>
             ArrayList<ArcEntryDescriptor> (arc file names and offset)  
            </td>
          </tr>    
         
         
         </tbody>  
</table>    
        
<br>


<br>

<table class="table" border="1">  
       <caption><strong>HTTP errors</strong></caption>
        <thead>  
          <tr>  
            <th>Error</th>  
            <th>Reason</th>                
          </tr>  
        </thead>  
        <tbody>  
          <tr>  
            <td>400 (Bad Request)</td>  
            <td>Caused by the input. Validation error etc.</td>    
          </tr>
           <tr>  
            <td>404 (Bad Request)</td>  
            <td>Not found</td>    
          </tr>    
            <tr>  
            <td>500 (Internal Server Error)</td>  
            <td>Server side errors, nothing to do about it.</td>    
          </tr>
        </tbody>  
</table>    
