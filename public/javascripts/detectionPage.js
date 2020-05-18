$(document).ready(function() {
    $("#detectionMainAlert").hide();

    //token sensitivity span value
    $('.inputRange').on('input', function() {
        $(this).next('.rangeValue').html(this.value);
    });

    //run JPlag ajax
    $("#runJPlagButton").click(function(e) {
        e.preventDefault();
        $.ajax({
            method: 'GET',
            url: 'http://localhost:9000/validateDetection',
            success : function(response) {
                if (response.message === "Pass") {
                    console.log(response.message);
                    window.location = 'http://localhost:9000/home/' + response.message;
                }
                else {
                    $('#detectionMainAlert').fadeIn(300).delay(30000).fadeOut(300).text(response.message);
                    //clear the file so that repeated upload works
                    $("#studentCodeUpload").val('');
                    $("#baseCodeUpload").val('');
                }
            }
        });
    });


    $("#studentCodeUpload").change(function(e) {
        $("#studentFileUploadForm").submit()
    });

    //student file upload ajax
    $("#studentFileUploadForm").submit(function(e) {
        var formData = new FormData(this);
        e.preventDefault();

        $.ajax({
            method: 'POST',
            url: 'http://localhost:9000/studentFileUpload',
            processData: false,
            contentType: false,
            data: formData,
            success : function(response) {
                $('#detectionMainAlert').fadeIn(300).delay(3000).fadeOut(300).text(response.message);
            }
        });
    });

    //settings submission ajax
    $("#settingsForm").submit(function(e) {
        var formData = new FormData(this);
        for (var pair of formData.entries()) {
            console.log(pair[0]+ ', ' + pair[1]);
        }
        e.preventDefault();

        $('#jplagSettingsModal').modal('hide');

        $.ajax({
            method: 'POST',
            url: 'http://localhost:9000/submitSettings',
            data: formData,
            contentType: false,
            processData: false,
            success : function(response) {
                $("#detectionMainAlert").fadeIn(100).delay(3000).fadeOut(300).text(response.message);
            }
        });
    });
});
