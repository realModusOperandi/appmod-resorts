async function checkCookieSetting() {
    console.log('Checking cookie preference with server');
    let result = await sendRequest("GET", `${window.location.origin}/resorts/rest/accepts-cookies`, null);

    result = JSON.parse(result);
    console.log(`==> preference: ${result.preference}`);
    if (result.preference !== 'true') {
        showCookieBanner();
    }
}

async function updateCookieSetting(cookiePreference) {
    // Only make a request to the backend if they answer 'true' so there is no session (and therefore cookie) created
    // in the event the user has chosen to reject cookies
    if (cookiePreference) {
        await sendRequest("POST", `${window.location.origin}/resorts/rest/accepts-cookies`, JSON.stringify({ preference: 'true' }));
    }
    await hideCookieBanner();
}

async function hideCookieBanner() {
    let banner = document.getElementById('cookie-banner');
    banner.classList.remove('cookie-banner--fullsize');

    // Wait for the transition animation to play
    await new Promise((resolve) => setTimeout(resolve, 500));
    banner.classList.remove('cookie-banner--visible');
}

async function showCookieBanner() {
    console.log('Showing cookie banner');
    let banner = document.getElementById('cookie-banner');
    banner.classList.add('cookie-banner--visible');

    // Need to delay for a moment in order for the CSS transition to work
    await new Promise((resolve) => setTimeout(resolve, 100));
    banner.classList.add('cookie-banner--fullsize');
    console.log(banner);
}