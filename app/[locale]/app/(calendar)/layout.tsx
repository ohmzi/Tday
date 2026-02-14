import NewFeaturesAnnouncement from "@/components/popups/NewFeaturesPopup";
import SidebarToggleContainer from "@/components/Sidebar/SidebarToggleContainer";

export default async function Layout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {


    return (
        <div
            className="h-full w-full overflow-hidden px-4 sm:px-6 sm:pt-4 lg:px-8 xl:px-10"
        >
            <SidebarToggleContainer />
            <NewFeaturesAnnouncement />
            {children}
        </div>

    );
}
