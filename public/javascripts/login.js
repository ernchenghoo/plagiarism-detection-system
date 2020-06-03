$(document).ready(function() {
    $('#loginPageAlert').hide();
    $('.navigation_bar').hide();

    //login ajax
    $("#login-form").submit(function(e) {
        e.preventDefault();
        var formData = new FormData(this);

        $.ajax({
            method: 'POST',
            url: 'http://localhost:9000/login',
            data: formData,
            processData: false,
            contentType: false,
            success : function(response) {
                console.log(response);
                if (response === "Pass") {

                    window.location = 'http://localhost:9000/home';
                }
                else {
                    $('#loginPageAlert').fadeIn(300).delay(3000).fadeOut(300).text(response);
                }
            }
        });
    });
});