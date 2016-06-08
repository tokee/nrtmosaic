
<h1>APINrtmosaic REST resources. </h1>
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
            <td>services/image?source=abc&x=123&y=456&=789</td>  
            <td>
            Params:source,x,y,x
            </td>  
            <td>
             Image
            </td>
          </tr>                                                                     
          <tr>                                 
         </tbody>  
</table>    
        
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
