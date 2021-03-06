$(document).ready(function() {
    $("#detectionMainAlert").hide();
    $("#loading_gif").hide();
    getUploadedFiles();
    getUploadedBaseFile();

    //token sensitivity span value
    $('.inputRange').on('input', function() {
        $(this).next('.rangeValue').html(this.value);
    });


    $("#runJPlagButton").click(function(e) {
        $("#runForm").submit()
    });
    
    $("#clear_uploaded_files_button").click(function(e) {
        clearUploadedFiles()
    });

    //run JPlag ajax
    $("#runForm").submit(function(e) {
        $("#loading_gif").show();
        e.preventDefault();
        var formData = new FormData(this);
        $.ajax({
            method: 'POST',
            url: document.location.href + '/validateDetection',
            data: formData,
            processData: false,
            contentType: false,
            success : function(response) {
                $("#loading_gif").hide();
                if (response.message === "Pass") {
                    window.location = 'https://plagiarism-detection-system.herokuapp.com/home';
                    // window.location = 'http://localhost:9000/home';
                }
                else {
                    $('#detectionMainAlert').fadeIn(300).delay(10000).fadeOut(300).text(response.message);
                    //clear the file so that repeated upload works
                    $("#studentCodeUpload").val('');
                    $("#baseCodeUpload").val('');
                }
            }
        });
    });

    function clearUploadedFiles() {
        $("#loading_gif").show();
        $.ajax({
            method: 'POST',
            url: document.location.href + '/clearUploadedFiles',
            processData: false,
            contentType: false,
            success : function(response) {
                $("#loading_gif").hide();
                $(".table_body_container").hide();
                $(".no_file_uploaded_message").show();
            }
        });
    }

    function getUploadedBaseFile() {
        $.ajax({
            method: 'GET',
            url: document.location.href + '/getUploadedBasefile',
            processData: false,
            contentType: false,
            success : function(response) {
                if (response.uploadedBaseFile === "None") {
                    $('#base_file_uploaded_container').hide();
                }
                else {
                    $('#base_file_uploaded_container').show();
                    $('#uploaded_baseFile_name').text(response.uploadedBaseFile);
                }

            }
        });
    }

    function getUploadedFiles(files) {
        $('tbody').empty();
        $(".no_file_uploaded_message").show();
        $(".table_body_container").hide();
        if (!files) {
            $.ajax({
                method: 'GET',
                url: document.location.href + '/getUploadedFiles',
                processData: false,
                contentType: false,
                success : function(response) {
                    if (response.uploadedFiles != null) {
                        var len = response.uploadedFiles.length;
                        var txt = "";
                        if(len > 0){
                            for(var i=0; i<len; i++){
                                if(response.uploadedFiles[i].fileName){
                                    txt += '<tr>';
                                    txt += '<td>' + response.uploadedFiles[i].fileName +  '</td>';
                                    txt += '<td><button type="button" class="btn delete table_delete_button"><i class="fa fa-trash-o fa-lg"></i></button></td>';
                                    txt += '</tr>'
                                }
                            }
                            if(txt !== ""){
                                $('tbody').append(txt);
                                $(".no_file_uploaded_message").hide();
                                $(".table_body_container").show();
                            }
                        }
                    }
                }
            });

        }
        else {
            var len = files.length;
            var txt = "";
            if(len > 0){
                for(var i=0; i<len; i++){
                    if(files[i].fileName){
                        txt += '<tr>';
                        txt += '<td>' + files[i].fileName +  '</td>';
                        txt += '<td><button type="button" class="btn delete table_delete_button"><i class="fa fa-trash-o fa-lg"></i></button></td>';
                        txt += '</tr>'
                    }
                }

                if(txt !== ""){
                    $('tbody').append(txt);
                    $(".no_file_uploaded_message").hide();
                    $(".table_body_container").show();
                }
            }
        }
    }

    //ajax request for deleting single uploaded file
    $(document).on("click", "button.delete", function () {
        var cRow = $(this).parents('tr');

        var fileName = $('td:nth-child(1)', cRow).text();
        cRow.remove();
        var rowCount = $('#uploaded_student_file_table tr').length;
        console.log(rowCount);
        if (rowCount === 1) {
            $(".table_body_container").hide();
            $(".no_file_uploaded_message").show();
        }
        $.ajax({
            method: 'POST',
            url: document.location.href + '/deleteSingleUploadedFile',
            processData : false,
            contentType: false,
            data: fileName,
            success : function(response) {
                // getUploadedFiles();
            }
        });
    });

    //ajax request for deleting single uploaded file
    $(document).on("click", "button#base_file_delete", function () {
        var fileName = $('#uploaded_baseFile_name').text();
        console.log(fileName);
        $.ajax({
            method: 'POST',
            url: document.location.href + '/deleteUploadedBaseFile',
            processData : false,
            contentType: false,
            data: fileName,
            success : function(response) {
                if (response === "Success") {
                    console.log()
                    $('#baseCodeUpload').val('');
                    $('#base_file_uploaded_container').hide();
                }
            }
        });
    });


    $("#studentCodeUpload").change(function(e) {
        $("#studentFileUploadForm").submit();
    });

    //student file upload ajax
    $("#studentFileUploadForm").submit(function(e) {
        $('#no_file_uploaded_message').text('Uploading... Please wait');
        $("#loading_gif").show();
        var formData = new FormData(this);
        e.preventDefault();

        $.ajax({
            method: 'POST',
            url: document.location.href + '/studentFileUpload',
            processData: false,
            contentType: false,
            data: formData,
            success : function(response) {
                $('#no_file_uploaded_message').text('You have not uploaded any files. Please do so to proceed.');
                $("#loading_gif").hide();
                $('#detectionMainAlert').fadeIn(300).delay(3000).fadeOut(300).text(response.message);
                getUploadedFiles(response.uploadedFiles);
                $("#studentCodeUpload").val('');
            }
        });
    });

    //settings submission ajax
    $("#settingsForm").submit(function(e) {
        $("#loading_gif").show();
        var formData = new FormData(this);
        e.preventDefault();

        $('#jplagSettingsModal').modal('hide');

        $.ajax({
            method: 'POST',
            url: document.location.href + '/submitSettings',
            data: formData,
            contentType: false,
            processData: false,
            success : function(response) {
                $("#loading_gif").hide();
                $("#detectionMainAlert").fadeIn(100).delay(3000).fadeOut(300).text(response.message);
                getUploadedBaseFile()
            }
        });
    });
});
