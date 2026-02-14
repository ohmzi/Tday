import NewFeaturesAnnouncement from "@/components/popups/NewFeaturesPopup";
import SidebarToggleContainer from "@/components/Sidebar/SidebarToggleContainer";

export default async function Layout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {


    return (
        <div
            className=
            "h-full w-full overflow-y-auto scrollbar-none px-4 pb-6 sm:px-6 sm:pt-4 sm:pb-8 lg:px-10 xl:px-14"
        >
            <SidebarToggleContainer />
            <NewFeaturesAnnouncement />
            {children}
        </div>

    );
}
