$(document).ready(function() {
    $('.logo_image').hide();
    $('.logout_button').hide();
    $('#registerPageAlert').hide();

    //register ajax
    $("#registration-form").submit(function(e) {
        e.preventDefault();
        var formData = new FormData(this);

        var verificationResult = verifyInput($('#username').val(), $('#password').val(), $('#re-enterPassword').val());

        $('#registerPageAlert').fadeIn(300).delay(3000).fadeOut(300).text(verificationResult);

        if (verificationResult === "Pass") {
            var accountObject = {};
            accountObject.username = $('#username').val();
            accountObject.password = $('#password').val();
            accountObject = JSON.stringify(accountObject);
            console.log(accountObject);


            $.ajax({
                method: 'POST',
                url: 'http://localhost:9000/register',
                data: accountObject,
                dataType: "json",
                contentType: 'application/json',
                success : function(response) {
                    if (response.message === "Pass") {
                        window.location = 'http://localhost:9000/home';
                    }
                    else {

                    }
                }
            });
        }
    });

    function verifyInput(username, password, reconfirmPassword) {
        console.log(password);
        if (username.length < 8 || password.length < 8) {
            return "Username/password too short"
        }
        else if (password !== reconfirmPassword) {
            return "Password and re-entered password are not the same"
        }
        else
            return "Pass"
    }
});