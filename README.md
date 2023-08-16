# Tuneberry

[![Clojars Project](https://img.shields.io/clojars/v/com.github.artbookspirit/tuneberry.svg)](https://clojars.org/com.github.artbookspirit/tuneberry)

[ClojureScript](https://clojurescript.org/) bindings for
[Spotify Web API](https://developer.spotify.com/documentation/web-api) with
retries and blocking mode.

## Quickstart

To display your Spotify user ID, run the code below:

```clojure
(require '[cljs.core.async :refer [<! go]]
         '[tuneberry.core :refer [tuneberry]]
         '[tuneberry.users :as u])

(def token "<OAuth 2.0 Spotify access token>")
(def tb (tuneberry token))
(go (prn (:id (<! (u/get-current-user-profile tb)))))
```

If by any chance you don't have a valid OAuth 2.0 access token for Spotify Web API in
your clipboard, _Tuneberry_ [test runner](#testing) has been adapted to help
you get through this painful process in minutes.

_Tuneberry_ is a collection of functions that wrap
[Spotify Web API](https://developer.spotify.com/documentation/web-api)
endpoints and return a [core.async](https://github.com/clojure/core.async) **channel** from which you can take
an endpoint's response. The response is a JSON object turned into a ClojureScript
(nested) map, where JSON keys are turned into keywords[^1].

Since `get-current-user-profile` is a wrapper around the
[Get Current User's Profile](https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile)
endpoint, we get the Spotify user ID simply by calling `:id`
on the response.

> [!NOTE]
> Production configurations require token refreshing and are covered in one of
> the [later sections](#setups-with-token-refresh).

## Passing Spotify API parameters

With _Tuneberry_, you pass url **query string parameters** as regular key-value pairs.

In the example below, we display the track listing for Katie Melua's
album _Love and Money_ using parameters `q` (search query) and `type` of
the [Search for Item](https://developer.spotify.com/documentation/web-api/reference/search) endpoint:

```clojure
(require '[tuneberry.search :refer [search]]
         '[clojure.pprint :refer [pprint]])

(go (let [res (<! (search tb
                          :q "artist:Katie Melua album:Love and Money"
                          :type "track"))]
      (pprint (->> (get-in res [:tracks :items])
                   (sort-by :track_number)
                   (map (juxt :track_number :uri :name))))))
```

...which should give an output similar to the one below:

```clojure
([1 "spotify:track:4xuxjqgOKjquDbuKDy1hto" "Golden Record"]
 [2 "spotify:track:7vKMYuPq6wqU4Le5AR9Kit" "Quiet Moves"]
 [3 "spotify:track:0maYSd1pQFI4Ody2toTDxx" "14 Windows"]
 [4 "spotify:track:2eW1Axi6Ruo5OtqOA6SzWO" "Lie In the Heat"]
 [5 "spotify:track:1L6AqrUhtUH3qTV6ImvTNw" "Darling Star"]
 [6 "spotify:track:4GJerh35GCooDcZXQHc2x3" "Reefs"]
 [7 "spotify:track:4MgSTJHFsjayq60GASHru0" "First Date"]
 [8 "spotify:track:17Jrz3JIr9PDN4IEW3wSYw" "Pick Me Up"]
 [9 "spotify:track:53OWCM7g2k2Ol42ykqvOwF" "Those Sweet Days"]
 [10 "spotify:track:69puCjWb1rrocZBah5s3GR" "Love & Money"])
```

**Body parameters** should be explicitly marked with the namespace `b/`[^2].
To relax a bit, let's play one of the above tracks using the endpoint
[Start/Resume Playback](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback):

```clojure
(require '[tuneberry.player :as p])

(let [lie-in-the-heat "spotify:track:2eW1Axi6Ruo5OtqOA6SzWO"]
  (p/start-or-resume-playback tb :b/uris [lie-in-the-heat]))
```

Make sure that you have Spotify player running on at least one of your
devices, otherwise the experience may not be entirely relaxing.

> [!NOTE]
> Thanks to [improvements](https://clojure.org/news/2021/03/18/apis-serving-people-and-programs)
> added in Clojure/Script 1.11, you can also specify keyword arguments as a
> single map:
>
> ```clojure
> (search tb {:q "artist:Katie Melua album:Love and Money"
>              :type "track"})
> ```

## Setting Tuneberry options

_Tuneberry_ options configure various library features, such as
[error suppression](#error-suppression)
or [blocking mode](#blocking-mode).

They are kept inside the `tuneberry` object and may be specified **during its
creation**:

```clojure
(def tb (tuneberry token {:blocking true, :smart false, :max-retry 5}))
```

Unspecified options are set to their default values, if such exist
(see [table below](#options-list)).

The `tuneberry` object is passed around to all API-calling functions
as the sole source of configuration. However, if you want to **quickly
add or change** an option for a single API call, you may put it in the `o/`
namespace.

Below we use this method to locally disable the `:smart`
[postprocessing](#postprocessing-with-smart-sel-and-post) option and receive a full http response map
(not just the http body containing the actual endpoint response):

```clojure
(require '[clojure.pprint :refer [pprint]]
         '[tuneberry.users :as u])

(go (pprint (<! (u/get-current-user-profile tb :o/smart false))))
```

```clojure
{:status          200,
 :success         true,
 :body            {:id "artbookspirit", ...},
 :headers         {...},
 :trace-redirects [...],
 :error-code      :no-error,
 :error-text      ""}
```

Detailed descriptions of the features can be found in later sections.

### Options list

| Option             | Default value              | Description                                                                           |
|--------------------|----------------------------|---------------------------------------------------------------------------------------|
| `:api-url`         | https://api.spotify.com/v1 | Common prefix of all Spotify Web API endpoints.                                       |
| `:blocking`        | false                      | Turns on [blocking mode](#blocking-mode).                                             |
| `:max-poll`        | 5                          | The maximum number of polls in blocking mode.                                         |
| `:poll-delays-fn`  | `(100 200 400 ...)`        | Returns a lazy sequence of wait intervals between successive polls in blocking mode.  |
| `:retry`           | `[500 502 503]`            | A list of [retry](#retries) criteria or false/nil to disable retries.                 |
| `:max-retry`       | 3                          | The maximum number of retries.                                                        |
| `:retry-delays-fn` | `(500 1000 2000 ...)`      | Returns a lazy sequence of wait intervals between successive retries.                 |
| `:smart`           | true                       | Turns on the [smart](#postprocessing-with-smart-sel-and-post) mode.                   |
| `:sel`             | N/A                        | Turns on the `:sel` [postprocessing](#postprocessing-with-smart-sel-and-post).        |
| `:sel-check`       | true                       | Specifies whether to return an error if the path passed with `:sel` returns nil.      |
| `:post`            | N/A                        | Turns on the `:post` [postprocessing](#postprocessing-with-smart-sel-and-post).       |
| `:post-check`      | true                       | Specifies whether to return an error if the function passed with `:post` returns nil. |

## Error handling

All API functions return a [core.async](https://github.com/clojure/core.async) channel which eventually contains:

- http response body on request success,
- an `ExceptionInfo` object on request failure.

Errors may come from a variety of sources, such as:

- http errors,
- [token refresh](#setups-with-token-refresh) errors,
- limit of polls reached in [blocking mode](#blocking-mode),
- limit of [retries](#retries) reached,
- a nil [postprocessing](#postprocessing-with-smart-sel-and-post) result.

> [!NOTE]
> [ExceptionInfo](https://clojuredocs.org/clojure.core/ex-info)
> is a subclass of `js/Error` that allows you to easily convey any extra information
> in the form of a plain ClosureScript map. The lack of neccessity to create
> a custom error class hierarchy means wun[^3] less problem with JavaScript
> intricacies.

Below we try to read a non-existent
[key sequence](#postprocessing-with-smart-sel-and-post) from the API response:

```clojure
(require '[tuneberry.player :as p])

(go
  (let [e (<! (p/get-available-devices tb :o/sel [:foo :bar]))]
    (prn e)))
```

The result is an `ExceptionInfo` holding the failed key sequence and the
original API response within its `data` property:

```clojure
{:message "no response path",
 :data    {:response {:devices
                      [{:id                 "39ee...",
                        :is_active          false,
                        :is_private_session false,
                        :is_restricted      false,
                        :name               "cuckoo",
                        :supports_volume    true,
                        :type               "Computer",
                        :volume_percent     100}]},
           :path     [:foo :bar]}}
```

### Throwing exceptions with `<?`

To avoid checking each API function response for `ExceptionInfo`, you can employ
the _Tuneberry_'s version of the commonly used `<?` macro[^4].

`<?` works exactly like `<!` except that if the value from the channel turns out
to be an instance of `js/Error`, it immediately throws it. This allows
you to use **usual try/catch** in the context of asynchronous channels.

The following short program, inspired by the fact that we still have at hand
a track listing for Katie Melua's album, checks if there is
any Katie's song in the playback queue and adds one if there isn't one already.

```clojure
(require '[tuneberry.core :refer [<?]]
         '[tuneberry.player :as p])

(go
  (try
    (let [queue (<? (p/get-user-queue tb))
          artists (->> queue
                       :queue
                       (mapcat :artists)
                       (map :name)
                       set)
          quiet-moves "spotify:track:7vKMYuPq6wqU4Le5AR9Kit"]
      (when (not (contains? artists "Katie Melua"))
        (<? (p/add-item-to-playback-queue tb :uri quiet-moves))
        (println "Quiet Moves added!")))
    (catch js/Error e
      (println "Error caught:" (ex-message e)))))
```

You can check that it handles errors correctly by adding something like
`:o/api-url "https://api.spotify.com/omgwtf"` to any of the API function calls and
observing the message:

```
Error caught: HTTP 404: Service not found
```

## Features

### Blocking mode

For many commands with side effects, the Spotify API works in a manner that
can be named _non-synchronous_ or _non-blocking_.
It seems that a `2XX` status code is returned by such endpoints as soon as
an action has been accepted for execution, not when the related
changes have actually appeared in the system.

For example:

- the [Get the User's Queue](https://developer.spotify.com/documentation/web-api/reference/get-queue)
  endpoint called immediately
  after [Add Item to Playback Queue](https://developer.spotify.com/documentation/web-api/reference/add-to-queue)
  sometimes shows that the item is not yet present in the queue,
- [Get Playback State](https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback)
  called right
  after [Skip To Next](https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-next-track)
  sometimes shows that the new track is not yet playing,
- [Get Playback State](https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback)
  called immediately
  after [Pause Playback](https://developer.spotify.com/documentation/web-api/reference/pause-a-users-playback)
  sometimes returns `is_playing = true`, which means that playback has not been stopped yet.

Such an API design has its advantages, increasing API's responsiveness
and reducing the server load. However, there are cases, like when using the
[player endpoints](https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback),
where we want to know the moment when a given action has taken effect.

Suppose we are writing an application to rate songs. We don't want to show
the user an active panel to enter a rating until we are sure that the song
currently selected by the application (and not the previous one) is already playing.
In many situations like that it is better to update the UI
a little later, if it guarantees that it will be synchronized with the state
of the player.

Blocking mode is implemented by **polling**: for a given API function with
side effects, another API endpoint is called in a loop (with backoffs) to
probe the system's state. The result isn't put into the returned channel
until the state meets a specific condition.

For example, for `tuneberry.player/pause-playback` polling continues until
`tuneberry.player/get-playback-state` returns `is_playing` as `false` (or the
maximum number of attempts is reached). Reactive code waiting on the returned
channel may fire a bit later, but never before the actual pause.

Blocking mode is disabled by default. It follows the zero-overhead principle
known from `C++`: _You don't pay for what you don't use_, because the number
of requests a Spotify application can send is subject to
[rate limits](https://developer.spotify.com/documentation/web-api/concepts/rate-limits).
However, it can save a lot of work by performing checks that would be placed
in the application code anyway.

To enable the blocking mode, simply call:

```clojure
(def tb (tuneberry token :blocking true))
```

If a given function supports the blocking mode, the release conditions can be
found in its description.

See also `:max-poll` and `:poll-delays-fn` in the [options list](#options-list).

### Retries

If an API function fails due to an http error, _Tuneberry_ retries the failed call
using simple preconfigured retry criteria and backoff strategy.

The `:retry` option contains a list of retry criteria, each being one of:

- a number `n` that must equal the http response status code for such criterion
  to be met,
- a vector `[n re]` where, in addition, a regular expression `re` needs to
  match a substring of the error message, taken from the http body.

If any of the criteria is satisfied, a failed API function call
is be retried up to `:max-retry` times. After that, another `ExceptionInfo`
object with message `retry limit reached` is returned.

> [!NOTE]
> The API function is repeated in its entirety, also when it consists of
> more than one http request, e.g. the actual API request and a number
> of polling requests in [blocking mode](#blocking-mode).

> [!IMPORTANT]
> See [below](#token-refresh-errors-and-retries) how to make token refresh
> errors also cause API functions' retries.

For illustrative purposes, let's break the access token, enable retries for
the `401` response code and show the result of reaching the retry limit:

```clojure
(require
  '[cljs.core.async :refer [<! go]]
  '[tuneberry.core :refer [tuneberry]]
  '[tuneberry.player :as p])

(go
  (let [tb (tuneberry "not-a-token")
        retry-criteria [500 502 503 [401 #"(?i)invalid.+token"]]
        e (<! (p/get-playback-state tb :o/retry retry-criteria))]
    (println "message:" (ex-message e))
    (println "number of attempts:" (-> e ex-data :nr-attempts))
    (println "last result message:" (-> e ex-data :last-result ex-message))))
```

```
message: retry limit reached
number of attempts: 4
last result message: HTTP 401: Invalid access token
```

If you want to disable retries altogether, set `:retry` to `false` or `nil`.

The **backoff strategy** is configured as the `:retry-delays-fn` function that
retrns a lazy sequence of wait intervals between successive retries.

By default, it is binary exponential backoff with the initial interval of
500 ms. That means _Tuneberry_ will pause for 500 ms before the first retry,
1000 ms before the second, 2000 ms before the third, and so on...
Before the 31st retry, it will pause for about 17 years, which should be
enough for Spotify dev team to bring back the service, if you only set
`:max-retry` adequately.

### Error suppression

If you try to either:

- pause an already paused playback
  using [Pause Playback](https://developer.spotify.com/documentation/web-api/reference/pause-a-users-playback),
- resume an already resumed playback
  using [Start/Resume Playback](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback),

Spotify Web API will respond with an `403` error saying:
`Player command failed: Restriction violated`.

It is doubtful that the described situation is an error at all,
and handling the related exception in the application code may be cumbersome.

For this reason:

1. _Tuneberry_ does not [wrap](#error-handling) these errors with an `ExceptionInfo` object,
2. the `<?` [macro](#throwing-exceptions-with-) does not throw an exception,
3. as the http body is normally returned, you can still check whether the Spotify
   API returned an error or not (no one will ever need it for anything).

### Postprocessing with `:smart`, `:sel` and `:post`

These options specify the final transformations performed on the result map.

**The `:smart` option returns for successful API calls only the http body,
containing the actual endpoint response.** Since in the absence of errors
the complete http response map (see [example](#setting-tuneberry-options)) is usually not needed,
`:smart` is enabled by default.

**The `:sel` option performs `get-in` on the API response** using the given
key sequence.
Being able to return only the parts of the response we are interested in
often results in cleaner code. Suppose we want to access several properties
of a single recording:

```clojure
(require '[tuneberry.core :refer [<?]]
         '[tuneberry.search :refer [search]])

(go (let [album (<? (search tb
                            :q "artist:Katie Melua album:Love and Money"
                            :type "album"
                            :o/sel [:albums :items 0]))]
      (println "name:" (:name album))
      (println "release_date:" (:release_date album))
      (println "total_tracks:" (:total_tracks album))))
```

```
name: Love & Money
release_date: 2023-03-24
total_tracks: 10
```

A variant without `:sel` would require an extra local binding:

```clojure
album (get-in res [:albums :items 0])
```

or the use of `get-in` in a single expression together with `search` and `<?`,
which obfuscates the code to a great extent.

Since we usually use key sequences that always exist and contain some data,
an error is returned when the sequence passed to `:sel` returns nil (see
example in [Error handling](#error-handling)).
This can be disabled by setting the `:sel-check` option to false.

**The `:post` option** is very similar to `:sel`, except that it **allows you to specify
any mapping function** that will be executed on the API response
(see [Options list](#options-list)).

## Setups with token refresh

The [Quickstart](#quickstart) section shows that the first parameter of the
`tuneberry` function (`token-src`) can be a string containing an OAuth 2.0
access token. This allows you to quickly test the
library in the REPL, but it is not suitable for a production setup.

In production configurations, `token-src` should be a token function that
returns a [core.async](https://github.com/clojure/core.async) channel containing a valid OAuth 2.0
access token for Spotify Web API.
The token function is called before each use of the Spotify API and is
expected to read the access token from a secure location. If the access token
has expired, it should be refreshed before returning and safely stored back.

The access token can be obtained and refreshed using several OAuth flows,
as described on the Spotify Web
API [Authorization page](https://developer.spotify.com/documentation/web-api/concepts/authorization).
_Tuneberry_ is tested with the most
reliable [Authorization Code with PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow),
but should also work with other OAuth flows (if not, please let me know).

The builder function below called `make-token-fn` creates a token function
that is used in _Tuneberry_ tests:

```clojure
(defn make-token-fn [client-id access-token]
  (let [token (atom access-token)]
    (fn []
      (go
        (when (token-expired? @token)
          (reset! token (<! (refresh-access-token
                              {:client-id     client-id
                               :refresh-token (:refresh_token @token)}))))
        (:access_token @token)))))
```

`make-token-fn` function
accepts [Spotify app Client ID](https://developer.spotify.com/documentation/web-api/concepts/apps)
needed for token refreshing together with an access token map obtained via
the PKCE authorization, containing keys such as:

- `:access_token` for Spotify Web API access,
- `:expires_in` to check whether the access token has expired,
- `:refresh_token` used in the refresh request.

The map (you can see an example at the end of the [Testing](#testing) section)
is stored in the `token` atom where it can be refreshed
by the returned token function each time the access token turns out to
be expired.

For _Tuneberry_ tests we don't need a "secure location" other than memory,
but in production setups you will probably want to securely persist
the access token map on disk or in a database.

Using the above builder is pretty straightforward:

```clojure
(tuneberry (make-token-fn client-id access-token))
```

It is assumed that the access token returned by the token function will
give access to the set of [scopes](https://developer.spotify.com/documentation/web-api/concepts/scopes)
required by the user. As with token lifetimes, _Tuneberry_ intentionally
does not control authorization scopes explicitly.

### Token refresh errors and retries

Http errors are [wrapped](#error-handling) by _Tuneberry_ with an `ExceptionInfo` object
containing special keys `:http-status` and `:http-message` in its data map, e.g.:

```clojure
{:message "HTTP 404: Service not found",
 :data    {:http-status  404,
           :http-message "Service not found",
           :cause        ...}}
```

Only if `:http-status` is present, the given `ExceptionInfo` is identified as
an http error for which the [retry criteria](#retries) are checked
(where `n` and `re` are compared with `:http-status` and `:http-message`,
respectively).

If you want token refresh errors to trigger retries,
they must be returned by the token function as such `ExceptionInfo` objects,
containing at least `:http-status` in the data map (`:http-message` is optional).

If needed, also additional criteria must be added to `:retry`.

## Testing

_Tuneberry_ tests include both unit tests and online integration tests with
Spotify Web API. They run in the browser using the [Shadow CLJS](https://github.com/thheller/shadow-cljs)
test runner generated for
the [`:browser-test` test target](https://shadow-cljs.github.io/docs/UsersGuide.html#target-browser-test).

The runner has been modified with the custom namespace `tuneberry.test.runner`
so that it performs
the [authorization code PKCE flow](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)
to obtain an access token used by tests to make API calls.

**Since the access token is displayed in the web console, it can be easily
copied and used elsewhere, e.g. [in the REPL](#getting-a-repl).**

### Prerequisites

To run the tests you will need:

- [Node.js](https://nodejs.org/),
- one of Java distributions,
- a [Spotify](https://www.spotify.com/) account,
- a Spotify app.

Creating a Spotify app is rather quick: see
the [official guidelines](https://developer.spotify.com/documentation/web-api/concepts/apps).
For the authentication flow to work correctly, you need to enter

```
http://127.0.0.1:8021/callback
```

as the `Redirect URI`, which is the address of the Shadow CLJS test runner
locally on your computer.

### Running the tests

To compile the tests and run the local test server, enter the _Tuneberry's_
root directory and execute:

```clojure
npx shadow-cljs watch test
```

After you see the `Build complete` message in your terminal, open the browser
and type

```
http://127.0.0.1:8021?client-id=<Client ID>
```

into the URL bar, where `Client ID` is your Spotify
app's [client id](https://developer.spotify.com/documentation/web-api/concepts/apps).
This is because the test runner needs to know on behalf of which application
it will request the access token. The client id will be stored in the browser's
local storage, so it only needs to be entered once.

> [!WARNING]
> _Tuneberry_ tests modify the state of the Spotify player:
> - turn off shuffle,
> - remove and add random tracks to the playback queue,
> - play tracks.
>
> Of course it's not harmful in any way, but make sure you're ok with it
> before running the tests.

If everything went well, you will be redirected to the Spotify login page
and then asked to authorize scopes required by the tests.

After you agree, the tests will be launched and their results displayed in
the browser window.

**To get the newly received access token, open the web console, look for a
message similar to the one below and copy `:access_token` from it:**

```
Received access token:
{:access_token "<OAuth 2.0 access token for Spotify Web API>",
 :token_type "Bearer",
 :expires_in 3600,
 :refresh_token "...",
 :scope "user-modify-playback-state ...",
 :expires_at ...}
```

### Getting a REPL

After executing `npx shadow-cljs watch test` and opening http://127.0.0.1:8021
in the browser, in a different terminal run:

```
shadow-cljs cljs-repl test
```

It's good to keep the web console open for logs and network errors.

## License

Copyright (C) 2023 Piotr Bartosik

Distributed under the Eclipse Public License, the same as Clojure.

[^1]: _Tuneberry_ returns modified result maps from [cljs-http](https://github.com/r0man/cljs-http).

[^2]: _Tuneberry_ follows a strategy to stand between the user and the API as
little as possible, so it does not decide which parameters are to be sent
in which way.

[^3]: An inside joke for those familiar with very opinionated yet truly
enlightening books by Douglas Crockford.

[^4]: See for example: http://swannodette.github.io/2013/08/31/asynchronous-error-handling
