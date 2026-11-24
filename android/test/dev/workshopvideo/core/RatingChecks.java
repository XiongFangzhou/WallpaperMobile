package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class RatingChecks {
    static void run(){
        check(!WorkshopRating.enough(5,4)&&WorkshopRating.enough(6,4),"ten votes are needed before a rating is shown");
        check(WorkshopRating.stars(.9f,3,1)==0,"too few votes show no stars");
        check(WorkshopRating.stars(.95f,900,10)==5&&WorkshopRating.stars(.62f,60,40)==3,"score maps to whole stars");
        check(WorkshopRating.stars(.01f,1,99)==1,"a rated item shows at least one star");
        check(WorkshopRating.stars(Float.NaN,50,50)==0,"invalid score shows no stars");
        check(WorkshopRating.approval(199,1)==99&&WorkshopRating.approval(0,0)==0&&WorkshopRating.approval(3,1)==75,"approval rounds down and handles no votes");
        check(WorkshopRating.rating(.9f,9,1)==4.5f&&WorkshopRating.rating(.917f,900,80)==4.6f&&WorkshopRating.rating(.9f,3,1)==0,"five-point rating keeps one decimal and needs ten votes");
        check(WorkshopRating.rating(0f,1,99)==.5f&&WorkshopRating.rating(1.2f,100,0)==5f&&WorkshopRating.ratingText(.917f,900,80).equals("4.6"),"five-point rating is clamped and formatted");
        check(WorkshopRating.count(999).equals("999")&&WorkshopRating.count(12345).equals("1.2万")&&WorkshopRating.count(19999).equals("1.9万")&&WorkshopRating.count(345678).equals("34万"),"vote counts use 万 above ten thousand");
    }
}
