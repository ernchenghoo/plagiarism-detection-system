$(document).ready(function() {

    $('#homepageAlert').hide();
    $('.ongoing_detection_table').hide();
    checkRunningDetections();

    $.ajax({
        method: 'GET',
        url: 'https://plagiarism-detection-system.herokuapp.com/checkForDetectionRan',
        success : function(response) {
            if (response.Status === "Run") {
                $.ajax({
                    method: 'POST',
                    url: 'https://plagiarism-detection-system.herokuapp.com/runJPlag',
                    success : function(response) {
                        if (response.Status === "Success") {
                            checkRunningDetections();
                            $('#homepageAlert').fadeIn(300).delay(3000).fadeOut(300).text("Plagiarism detection has complete!");
                        }
                        else {
                            $('#detectionSuccessAlert').fadeIn(300).delay(3000).fadeOut(300).text(response.Status);
                        }
                    }
                });
            }
        }
    });

    function checkRunningDetections() {
        $.ajax({
            method: 'GET',
            url: 'https://plagiarism-detection-system.herokuapp.com/checkForRunningDetection',
            success : function(response) {
                var len = response.length;
                var txt = "";
                if(len > 0){
                    for(var i=0; i<len; i++){
                        if(response[i].detectionName && response[i].detectionDateTime){
                            txt += "<tr><td>"+response[i].detectionName+"</td><td>"+response[i].detectionDateTime+"</td></tr>";
                        }
                    }

                    if(txt !== ""){
                        $("#ongoing_detection_table").append(txt);
                        $(".ongoing_detection_table").show();
                    }
                }
                else {
                    $(".ongoing_detection_table").hide();
                }
            }
        });
    }


});