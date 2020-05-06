$(document).ready(function() {
    $('#fileUploadAlert').hide();

    $("#file-upload").on("change", function(e) {

        var formData = new FormData($("#fileUploadForm")[0]);
        e.preventDefault();

        $.ajax({
            method: 'POST',
            url: 'http://localhost:9000/uploadFile',
            processData: false,
            contentType: false,
            data: formData,
            success : function(response) {
                $('#fileUploadAlert').show();
            }
        });
    });
});
