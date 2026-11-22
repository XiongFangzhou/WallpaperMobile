package dev.workshopvideo.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static dev.workshopvideo.core.CoreChecks.*;

final class LibraryChecks {
    record Item(String id,String title){}
    static void run() throws Exception {
        List<Item> original=new ArrayList<>(List.of(new Item("123","Same title")));
        Item independent=new Item("456","Same title");var next=FavoriteIndex.toggle(original,independent,Item::id);
        check(original.size()==1&&next.size()==2,"favorites isolated snapshot and distinct workshop IDs");
        check(FavoriteIndex.contains(next,"456",Item::id),"find favorite without any download record");
        var removed=FavoriteIndex.toggle(next,new Item("123","Updated title"),Item::id);
        check(removed.equals(List.of(independent)),"remove favorite by workshop ID despite changed metadata");
        check(FavoriteIndex.toggle(List.of(independent,independent),independent,Item::id).isEmpty(),"remove duplicate legacy favorites");
        check(FavoriteIndex.toggle(List.of(),new Item("18446744073709551615","Unsigned ID"),Item::id).size()==1,"unsigned workshop ID");
        rejects(()->FavoriteIndex.toggle(List.of(),new Item("0","Invalid"),Item::id),"zero favorite ID");
        rejects(()->FavoriteIndex.toggle(List.of(),new Item("18446744073709551616","Invalid"),Item::id),"overflow favorite ID");
        rejects(()->FavoriteIndex.toggle(List.of(),new Item("../1","Invalid"),Item::id),"malformed favorite ID");
        Path sandbox=Files.createTempDirectory("wallpaper-files-");
        try{
            File library=sandbox.resolve("library.video").toFile();byte[] content=new byte[8192];new Random(42).nextBytes(content);Files.write(library.toPath(),content);
            File wallpapers=sandbox.resolve("wallpapers").toFile();File active=WallpaperFiles.copy(wallpapers,library);
            check(active.isFile()&&Arrays.equals(content,Files.readAllBytes(active.toPath())),"pin original bytes into independent wallpaper copy");
            File candidate=WallpaperFiles.copy(wallpapers,library);File old=WallpaperFiles.copy(wallpapers,library);
            WallpaperFiles.cleanup(wallpapers,Set.of(active.getName(),candidate.getName(),old.getName()));
            check(old.isFile(),"previous wallpaper retained for undo");
            WallpaperFiles.cleanup(wallpapers,Set.of(active.getName(),candidate.getName()));
            check(active.isFile()&&candidate.isFile()&&!old.exists(),"cleanup retains active and preview, reclaims obsolete copy");
            library.delete();check(Arrays.equals(content,Files.readAllBytes(active.toPath())),"deleting library file preserves active wallpaper");
            WallpaperFiles.cleanup(wallpapers,Set.of(active.getName()));check(active.isFile()&&!candidate.exists(),"cancel preview preserves old active copy");
            WallpaperFiles.cleanup(wallpapers,Set.of());check(!active.exists(),"explicit cleanup after switching wallpaper frees pinned copy");
            rejects(()->WallpaperFiles.path(wallpapers,"../library.video"),"wallpaper path traversal");
            rejects(()->WallpaperFiles.copy(wallpapers,library),"missing source wallpaper");
        }finally{try(var paths=Files.walk(sandbox)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
