import React from 'react';
import {
    IconButton,
    InputAdornment,
    Typography,
    Button,
    CssBaseline,
    TextField,
    Link,
    Card,
    CardContent,
    CardActions,
} from '@material-ui/core';
import Visibility from '@material-ui/icons/Visibility';
import VisibilityOff from '@material-ui/icons/VisibilityOff';
import WarningIcon from '@material-ui/icons/Warning';
import ErrorOutlineIcon from '@material-ui/icons/ErrorOutline';
import Spinner from '../Spinner/Spinner';
import './Login.css';

export default class Login extends React.Component {
    constructor(props) {
        super(props);

        this.state = {
            username: '',
            password: '',
            errorMessage: '',
            showPassword: false,
        };

        this.handleClickShowPassword = this.handleClickShowPassword.bind(this);
        this.handleChange = this.handleChange.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);
        this.backToLogin = this.backToLogin.bind(this);
        this.onKeyDown = this.onKeyDown.bind(this);
    }

    /**
     * Detect caps lock being on when typing.
     * @param keyEvent On key down event.
     */
    onKeyDown = keyEvent => {
        this.setState({ warning: false });
        if (keyEvent.getModifierState('CapsLock')) {
            this.setState({ warning: true });
        } else {
            this.setState({ warning: false });
        }
    };

    handleClickShowPassword(showPassword) {
        this.setState({ showPassword: !showPassword });
    }

    isDisabled = () => {
        const { isFetching } = this.props;
        return isFetching;
    };

    handleError = auth => {
        const { error, expired } = auth;
        let messageText;
        let invalidNewPassword;
        let isSuspended;
        const { authentication } = this.props;
        // eslint-disable-next-line global-require
        const errorMessages = require('../../error-messages.json');
        if (
            error.messageNumber !== undefined &&
            error.messageNumber !== null &&
            error.messageType !== undefined &&
            error.messageType !== null
        ) {
            messageText = `Unexpected error, please try again later (${error.messageNumber})`;
            const filter = errorMessages.messages.filter(
                x => x.messageKey != null && x.messageKey === error.messageNumber
            );
            invalidNewPassword = error.messageNumber === 'ZWEAT604E' || error.messageNumber === 'ZWEAT413E';
            isSuspended = error.messageNumber === 'ZWEAT414E';
            if (filter.length !== 0) {
                if (filter[0].messageKey === 'ZWEAS120E') {
                    messageText = `${filter[0].messageText}`;
                } else {
                    messageText = `(${error.messageNumber}) ${filter[0].messageText}`;
                }
            }
            if (invalidNewPassword || isSuspended) {
                messageText = `${filter[0].messageText}`;
            }
        } else if (error.status === 401 && authentication.sessionOn) {
            messageText = `(${errorMessages.messages[0].messageKey}) ${errorMessages.messages[0].messageText}`;
            authentication.onCompleteHandling();
        } else if (error.status === 500) {
            messageText = `(${errorMessages.messages[1].messageKey}) ${errorMessages.messages[1].messageText}`;
        }
        return { messageText, expired, invalidNewPassword, isSuspended };
    };

    handleChange(e) {
        const { name, value } = e.target;
        this.setState({ [name]: value });
        if (name === 'repeatNewPassword') {
            const { newPassword } = this.state;
            const { validateInput } = this.props;
            validateInput({ newPassword, repeatNewPassword: value });
        }
    }

    backToLogin() {
        this.setState({ newPassword: null });
        this.setState({ repeatNewPassword: null });
        const { returnToLogin } = this.props;
        returnToLogin();
    }

    handleSubmit(e) {
        e.preventDefault();

        const { username, password, newPassword } = this.state;
        const { login } = this.props;
        if (username && password && newPassword) {
            login({ username, password, newPassword });
        } else if (username && password) {
            login({ username, password });
        }
    }

    render() {
        const { username, password, errorMessage, showPassword, warning, newPassword, repeatNewPassword } = this.state;
        const { authentication, isFetching } = this.props;
        let error = { messageText: null, expired: false, invalidNewPassword: true };
        if (
            authentication !== undefined &&
            authentication !== null &&
            authentication.error !== undefined &&
            authentication.error !== null
        ) {
            error = this.handleError(authentication);
            if (error.isSuspended) {
                return (
                    <div className="login-object">
                        <div className="susp-card">
                            <Card variant="outlined">
                                <CardContent className="cardTitle">
                                    <Typography sx={{ fontSize: 14 }} color="text.secondary" gutterBottom>
                                        <WarningIcon style={{ color: '#de1b1b' }} size="2rem" />
                                        <b>{error.messageText}</b>
                                    </Typography>
                                </CardContent>
                                <CardContent>
                                    <Typography variant="body2">
                                        <b>{username}</b> account has been suspended.
                                    </Typography>
                                    <br />
                                    <Typography variant="body2">
                                        Contact your security administrator to unsuspend your account.
                                    </Typography>
                                </CardContent>
                                <CardActions>
                                    <Button
                                        variant="outlined"
                                        className="backBtn"
                                        color="primary"
                                        label=""
                                        size="medium"
                                        style={{ border: 'none' }}
                                        onClick={this.backToLogin}
                                        data-testid="suspendedBackToLogin"
                                    >
                                        RETURN TO LOGIN
                                    </Button>
                                </CardActions>
                            </Card>
                        </div>
                    </div>
                );
            }
        } else if (errorMessage) {
            error.messageText = errorMessage;
        }

        return (
            <div className="login-object">
                <div className="susp-card">
                    <div className="w-form">
                        <form
                            id="login-form"
                            name="login-form"
                            data-testid="login-form"
                            data-name="Login Form"
                            className="form"
                            onSubmit={this.handleSubmit}
                        >
                            <CssBaseline />
                            <div className="text-block-4">API Catalog</div>
                            <br />
                            {error.messageText !== undefined &&
                                error.messageText !== null && (
                                    <div id="error-message">
                                        <WarningIcon style={{ color: '#de1b1b' }} size="2rem" />
                                        {error.messageText}
                                    </div>
                                )}
                            {!error.expired && (
                                <div>
                                    <Typography className="login-typo" variant="subtitle1" gutterBottom component="div">
                                        Login
                                    </Typography>
                                    <Typography variant="subtitle2" gutterBottom component="div">
                                        Please enter your mainframe username and password to access this resource
                                    </Typography>
                                    <TextField
                                        label="Username"
                                        data-testid="username"
                                        className="formfield"
                                        variant="outlined"
                                        required
                                        error={!!error.messageText}
                                        fullWidth
                                        id="username"
                                        name="username"
                                        value={username}
                                        onChange={this.handleChange}
                                        autoComplete="on"
                                        autoFocus
                                    />
                                    <br />
                                    <TextField
                                        id="password"
                                        htmlFor="outlined-adornment-password"
                                        label="Password"
                                        data-testid="password"
                                        className="formfield"
                                        variant="outlined"
                                        required
                                        error={!!error.messageText}
                                        fullWidth
                                        name="password"
                                        type={showPassword ? 'text' : 'password'}
                                        value={password}
                                        onKeyDown={this.onKeyDown}
                                        onChange={this.handleChange}
                                        caption="Default: password"
                                        autoComplete="on"
                                        InputProps={{
                                            endAdornment: (
                                                <InputAdornment position="end">
                                                    {error.messageText && <ErrorOutlineIcon className="errorIcon" />}
                                                    <IconButton
                                                        aria-label="toggle password visibility"
                                                        edge="end"
                                                        onClick={() => this.handleClickShowPassword(showPassword)}
                                                    >
                                                        {showPassword ? <VisibilityOff /> : <Visibility />}
                                                    </IconButton>
                                                </InputAdornment>
                                            ),
                                        }}
                                    />
                                    {warning && <Link underline="hover"> Caps Lock is ON! </Link>}
                                    <div className="login-btns" id="loginButton">
                                        <Button
                                            variant="contained"
                                            color="primary"
                                            label=""
                                            size="medium"
                                            style={{ border: 'none' }}
                                            type="submit"
                                            data-testid="submit"
                                            disabled={this.isDisabled()}
                                        >
                                            LOG IN
                                        </Button>
                                    </div>
                                </div>
                            )}
                            {error.expired && (
                                <div>
                                    <TextField
                                        id="newPassword"
                                        htmlFor="outlined-adornment-password"
                                        label="New Password"
                                        data-testid="newPassword"
                                        className="formfield"
                                        variant="outlined"
                                        required
                                        error={error.invalidNewPassword}
                                        fullWidth
                                        name="newPassword"
                                        type={showPassword ? 'text' : 'password'}
                                        value={newPassword}
                                        onKeyDown={this.onKeyDown}
                                        onChange={this.handleChange}
                                        caption="Default: new password"
                                        autoComplete="on"
                                        InputProps={{
                                            endAdornment: (
                                                <InputAdornment position="end">
                                                    {error.messageText && <ErrorOutlineIcon className="errorIcon" />}
                                                    <IconButton
                                                        aria-label="toggle password visibility"
                                                        edge="end"
                                                        onClick={() => this.handleClickShowPassword(showPassword)}
                                                    >
                                                        {showPassword ? <VisibilityOff /> : <Visibility />}
                                                    </IconButton>
                                                </InputAdornment>
                                            ),
                                        }}
                                    />
                                    <br />
                                    <TextField
                                        id="repeatNewPassword"
                                        htmlFor="outlined-adornment-password"
                                        label="Repeat New Password"
                                        data-testid="repeatNewPassword"
                                        className="formfield"
                                        variant="outlined"
                                        required
                                        error={error.invalidNewPassword}
                                        fullWidth
                                        name="repeatNewPassword"
                                        type={showPassword ? 'text' : 'password'}
                                        value={repeatNewPassword}
                                        onKeyDown={this.onKeyDown}
                                        onChange={this.handleChange}
                                        caption="Default: Repeat new password"
                                        autoComplete="on"
                                        InputProps={{
                                            endAdornment: (
                                                <InputAdornment position="end">
                                                    {error.messageText && <ErrorOutlineIcon className="errorIcon" />}
                                                    <IconButton
                                                        aria-label="toggle password visibility"
                                                        edge="end"
                                                        onClick={() => this.handleClickShowPassword(showPassword)}
                                                    >
                                                        {showPassword ? <VisibilityOff /> : <Visibility />}
                                                    </IconButton>
                                                </InputAdornment>
                                            ),
                                        }}
                                    />
                                    <div className="login-btns">
                                        <Button
                                            variant="outlined"
                                            className="backBtn"
                                            color="primary"
                                            label=""
                                            size="medium"
                                            style={{ border: 'none' }}
                                            onClick={this.backToLogin}
                                            data-testid="backToLogin"
                                            disabled={this.isDisabled()}
                                        >
                                            BACK
                                        </Button>
                                        <Button
                                            variant="contained"
                                            className="updateBtn"
                                            color="primary"
                                            label=""
                                            size="medium"
                                            style={{ border: 'none' }}
                                            type="submit"
                                            data-testid="submitChange"
                                            disabled={!repeatNewPassword || error.invalidNewPassword}
                                        >
                                            CHANGE PASSWORD
                                        </Button>
                                    </div>
                                </div>
                            )}
                            <Spinner
                                className="formfield form-spinner"
                                label=""
                                isLoading={isFetching}
                                css={{
                                    position: 'relative',
                                    top: '70px',
                                    marginLeft: '-64px',
                                }}
                            />
                        </form>
                    </div>
                </div>
            </div>
        );
    }
}
