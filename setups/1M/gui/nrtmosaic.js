// http://stackoverflow.com/questions/12460378/how-to-get-json-from-url-in-javascript
var getJSON = function(url, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open("get", url, true);
    xhr.responseType = "json";
	xhr.onload = function() {
      var status = xhr.status;
	  var resp;
	  if (typeof xhr.response == "string" ){
		resp = JSON.parse(xhr.response);
	  } else {
		  resp = xhr.response;
	  } 
	 if (status == 200) {
		callback(null, resp);
      } else {
        callback(status);
      }
    };
    xhr.send();
};
