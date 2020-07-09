$(document).ready(function() {

    $('#homepageAlert').hide();
    $('.ongoing_detection_table').hide();
    checkRunningDetections();

    $.ajax({
        method: 'GET',
        url: document.location.href + '/checkForDetectionRan',
        success : function(response) {
            if (response.Status === "Run") {
                $.ajax({
                    method: 'POST',
                    url: document.location.href + '/runJPlag/' + response.DetectionID,
                    success : function(response) {
                        console.log("Run returned response");
                        console.log(response.Status);
                        if (response.Status === "Success") {
                            checkRunningDetections("Ran");
                        }
                        else if(response.Status === "Running") {
                            console.log("Running");
                            checkRunningDetections();
                        }
                        else {
                            $('#detectionSuccessAlert').fadeIn(300).delay(3000).fadeOut(300).text(response.Status);
                        }
                    }
                });
            }
        }
    });

    function checkRunningDetections(runStatus) {
        $.ajax({
            method: 'GET',
            url: document.location.href + '/checkForRunningDetection',
            success : function(response) {

                $("#ongoing_detection_table tbody > tr").empty();
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
                if (runStatus === "Ran") {
                    $('#homepageAlert').fadeIn(300).delay(3000).fadeOut(300).text("Plagiarism detection has complete!");
                }
            }
        });
    }


});