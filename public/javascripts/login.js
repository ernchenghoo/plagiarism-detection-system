$(document).ready(function() {
    $('#loginPageAlert').hide();
    $('.navigation_bar').hide();

    //login ajax
    $("#login-form").submit(function(e) {
        e.preventDefault();
        var formData = new FormData(this);

        $.ajax({
            method: 'POST',
            url: 'https://plagiarism-detection-system.herokuapp.com/login',
            data: formData,
            crossDomain: true,
            processData: false,
            contentType: false,
            success : function(response) {
                console.log(response);
                if (response === "Pass") {
                    window.location = 'https://plagiarism-detection-system.herokuapp.com/home';
                }
                else {
                    $('#loginPageAlert').fadeIn(300).delay(3000).fadeOut(300).text(response);
                }
            }
        });
    });
});