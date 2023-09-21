import { useContext, useEffect, useState } from 'react';
import {User, UserManager} from 'oidc-client-ts';
import AuthContext from "./AuthContext.ts";
import {ListsUser} from "../api/sources/model.ts";

export const useAuth = () => {
    const userManager: UserManager = useContext(AuthContext) as UserManager;
    const [currentUser, setCurrentUser] = useState<ListsUser | null>(null);
    const { search } = window.location;

    useEffect(() => {
        let result = userManager.getUser();
        result.then((user:User | null) => {
            if (user) {
                const roles = (user?.profile?.role || []) as string[];
                const userId = user?.profile?.userid as string || '';
                setCurrentUser({
                    user: user,
                    userId: userId,
                    isAdmin: roles.includes('ROLE_ADMIN'),
                    roles: roles
                });
                localStorage.setItem('access_token', user.access_token);

            } else if (search.includes('code=') && search.includes('state=')) {
                const params = new URLSearchParams(window.location.search);

                (async () => {
                    // Attempt to retrieve an access token
                    try {
                        const user = await userManager.signinRedirectCallback();
                        await userManager.storeUser(user);
                        const roles = (user?.profile?.role || []) as string[];
                        const userId = user?.profile?.userid as string || '';
                        setCurrentUser({
                            user: user,
                            userId: userId,
                            isAdmin: roles.includes('ROLE_ADMIN'),
                            roles: roles
                        });
                        localStorage.setItem('access_token', user.access_token);
                    } catch (error) {
                        console.log("Problem retrieving access token: " + error);
                    }

                    // Replace the URL to one without the auth query params
                    params.delete('code');
                    params.delete('state');
                    params.delete('client_id');
                    window.history.replaceState(
                        null,
                        '',
                        `${window.location.origin}${window.location.pathname}?${params.toString()}`
                    );
                })();
            }
        });
    }, []);
    return { currentUser };
};